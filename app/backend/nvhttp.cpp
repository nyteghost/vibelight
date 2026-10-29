#include <QRegularExpression>
#include "nvcomputer.h"
#include <Limelight.h>

#include <QDebug>
#include <QUuid>
#include <QtNetwork/QNetworkReply>
#include <QEventLoop>
#include <QTimer>
#include <QElapsedTimer>
#include <QFile>
#include <QFileInfo>
#include <QUrlQuery>
#include <QXmlStreamReader>
#include <QSslKey>
#include <QImageReader>
#include <QtEndian>
#include <QNetworkProxy>
#include <QJsonDocument>
#include <QJsonObject>
#include <QJsonArray>
#include <QStandardPaths>
#include <QDir>
#include <QTemporaryFile>

#define FAST_FAIL_TIMEOUT_MS 2000
#define REQUEST_TIMEOUT_MS 5000
#define LAUNCH_TIMEOUT_MS 120000
#define RESUME_TIMEOUT_MS 30000
#define QUIT_TIMEOUT_MS 30000

#if QT_VERSION < QT_VERSION_CHECK(6, 0, 0)
#define XML_NAME_EQUALS(x, y) ((x) == (y))
#else
#define XML_NAME_EQUALS(x, y) ((x) == (u##y))
#endif

NvHTTP::NvHTTP(NvAddress address, uint16_t httpsPort, QSslCertificate serverCert, bool useTrueUid, QNetworkAccessManager* nam) :
    m_Nam(nam ? nam : new QNetworkAccessManager(this)),
    m_ServerCert(serverCert),
    m_UseTrueUid(useTrueUid)
{
    m_BaseUrlHttp.setScheme("http");
    m_BaseUrlHttps.setScheme("https");

    setAddress(address);
    setHttpsPort(httpsPort);

    // Never use a proxy server
    QNetworkProxy noProxy(QNetworkProxy::NoProxy);
    m_Nam->setProxy(noProxy);
}

NvHTTP::NvHTTP(NvComputer* computer, QNetworkAccessManager* nam) :
    NvHTTP(computer->activeAddress, computer->activeHttpsPort, computer->serverCert, !computer->isNvidiaServerSoftware, nam)
{
}

void NvHTTP::setServerCert(QSslCertificate serverCert)
{
    m_ServerCert = serverCert;
}

void NvHTTP::setAddress(NvAddress address)
{
    Q_ASSERT(!address.isNull());

    m_Address = address;

    m_BaseUrlHttp.setHost(address.address());
    m_BaseUrlHttps.setHost(address.address());

    m_BaseUrlHttp.setPort(address.port());
}

void NvHTTP::setHttpsPort(uint16_t port)
{
    m_BaseUrlHttps.setPort(port);
}

void NvHTTP::setTrueUid(bool useTrueUid)
{
    m_UseTrueUid = useTrueUid;
}

NvAddress NvHTTP::address()
{
    return m_Address;
}

QSslCertificate NvHTTP::serverCert()
{
    return m_ServerCert;
}

uint16_t NvHTTP::httpPort()
{
    return m_BaseUrlHttp.port();
}

uint16_t NvHTTP::httpsPort()
{
    return m_BaseUrlHttps.port();
}

QVector<int>
NvHTTP::parseQuad(QString quad)
{
    QVector<int> ret;

    // Return an empty vector for old GFE versions
    // that were missing GfeVersion.
    if (quad.isEmpty()) {
        return ret;
    }

    QStringList parts = quad.split(".");
    ret.reserve(parts.length());
    for (int i = 0; i < parts.length(); i++)
    {
        ret.append(parts.at(i).toInt());
    }

    return ret;
}

int
NvHTTP::getCurrentGame(QString serverInfo)
{
    // GFE 2.8 started keeping currentgame set to the last game played. As a result, it no longer
    // has the semantics that its name would indicate. To contain the effects of this change as much
    // as possible, we'll force the current game to zero if the server isn't in a streaming session.
    QString serverState = getXmlString(serverInfo, "state");
    if (serverState.endsWith("_SERVER_BUSY"))
    {
        return getXmlString(serverInfo, "currentgame").toInt();
    }
    else
    {
        return 0;
    }
}

QString
NvHTTP::getServerInfo(NvLogLevel logLevel, bool fastFail)
{
    QString serverInfo;

    // Check if we have a pinned cert and HTTPS port for this host yet
    if (!m_ServerCert.isNull() && httpsPort() != 0)
    {
        try
        {
            // Always try HTTPS first, since it properly reports
            // pairing status (and a few other attributes).
            serverInfo = openConnectionToString(m_BaseUrlHttps,
                                                "serverinfo",
                                                nullptr,
                                                fastFail ? FAST_FAIL_TIMEOUT_MS : REQUEST_TIMEOUT_MS,
                                                logLevel);
            // Throws if the request failed
            verifyResponseStatus(serverInfo);
        }
        catch (const GfeHttpResponseException& e)
        {
            if (e.getStatusCode() == 401)
            {
                // Certificate validation error, fallback to HTTP
                serverInfo = openConnectionToString(m_BaseUrlHttp,
                                                    "serverinfo",
                                                    nullptr,
                                                    fastFail ? FAST_FAIL_TIMEOUT_MS : REQUEST_TIMEOUT_MS,
                                                    logLevel);
                verifyResponseStatus(serverInfo);
            }
            else
            {
                // Rethrow real errors
                throw e;
            }
        }
    }
    else
    {
        // Only use HTTP prior to pairing or fetching HTTPS port
        serverInfo = openConnectionToString(m_BaseUrlHttp,
                                            "serverinfo",
                                            nullptr,
                                            fastFail ? FAST_FAIL_TIMEOUT_MS : REQUEST_TIMEOUT_MS,
                                            logLevel);
        verifyResponseStatus(serverInfo);

        // Populate the HTTPS port
        uint16_t httpsPort = getXmlString(serverInfo, "HttpsPort").toUShort();
        if (httpsPort == 0) {
            httpsPort = DEFAULT_HTTPS_PORT;
        }
        setHttpsPort(httpsPort);

        // If we just needed to determine the HTTPS port, we'll try again over
        // HTTPS now that we have the port number
        if (!m_ServerCert.isNull()) {
            return getServerInfo(logLevel, fastFail);
        }
    }

    return serverInfo;
}

void
NvHTTP::startApp(QString verb,
                 bool isGfe,
                 int appId,
                 PSTREAM_CONFIGURATION streamConfig,
                 bool sops,
                 bool localAudio,
                 int gamepadMask,
                 bool persistGameControllersOnDisconnect,
                 QString& rtspSessionUrl,
                 const QString& twoFactorCode,
                 const QString& monitorId)
{
    int riKeyId;

    memcpy(&riKeyId, streamConfig->remoteInputAesIv, sizeof(riKeyId));
    riKeyId = qFromBigEndian(riKeyId);

    QString response =
            openConnectionToString(m_BaseUrlHttps,
                                   verb,
                                   "appid="+QString::number(appId)+
                                   "&mode="+QString::number(streamConfig->width)+"x"+
                                   QString::number(streamConfig->height)+"x"+
                                   // Using an FPS value over 60 causes SOPS to default to 720p60,
                                   // so force it to 0 to ensure the correct resolution is set. We
                                   // used to use 60 here but that locked the frame rate to 60 FPS
                                   // on GFE 3.20.3. We don't need this hack for Sunshine.
                                   QString::number((streamConfig->fps > 60 && isGfe) ? 0 : streamConfig->fps)+
                                   "&additionalStates=1&sops="+QString::number(sops ? 1 : 0)+
                                   "&rikey="+QByteArray(streamConfig->remoteInputAesKey, sizeof(streamConfig->remoteInputAesKey)).toHex()+
                                   "&rikeyid="+QString::number(riKeyId)+
                                   ((streamConfig->supportedVideoFormats & VIDEO_FORMAT_MASK_10BIT) ?
                                       "&hdrMode=1&clientHdrCapVersion=0&clientHdrCapSupportedFlagsInUint32=0&clientHdrCapMetaDataId=NV_STATIC_METADATA_TYPE_1&clientHdrCapDisplayData=0x0x0x0x0x0x0x0x0x0x0" :
                                        "")+
                                   "&localAudioPlayMode="+QString::number(localAudio ? 1 : 0)+
                                   "&surroundAudioInfo="+QString::number(SURROUNDAUDIOINFO_FROM_AUDIO_CONFIGURATION(streamConfig->audioConfiguration))+
                                   "&remoteControllersBitmap="+QString::number(gamepadMask)+
                                   "&gcmap="+QString::number(gamepadMask)+
                                   "&gcpersist="+QString::number(persistGameControllersOnDisconnect ? 1 : 0)+
                                   (twoFactorCode.isEmpty() ? QString() :
                                       "&vibebeam_2fa="+QString::fromLatin1(QUrl::toPercentEncoding(twoFactorCode)))+
                                   (monitorId.isEmpty() ? QString() :
                                    monitorId == "@virtual" ? QString("&virtualDisplay=1") :
                                       "&vibeMonitor="+QString::fromLatin1(QUrl::toPercentEncoding(monitorId)))+
                                   LiGetLaunchUrlQueryParameters(),
                                   LAUNCH_TIMEOUT_MS);

    qInfo() << "Launch response:" << response;

    // Throws if the request failed
    verifyResponseStatus(response);

    rtspSessionUrl = getXmlString(response, "sessionUrl0");
}

void
NvHTTP::quitApp()
{
    QString response =
            openConnectionToString(m_BaseUrlHttps,
                                   "cancel",
                                   nullptr,
                                   QUIT_TIMEOUT_MS);

    qInfo() << "Quit response:" << response;

    // Throws if the request failed
    verifyResponseStatus(response);

    // Newer GFE versions will just return success even if quitting fails
    // if we're not the original requester.
    if (getCurrentGame(getServerInfo(NvHTTP::NVLL_ERROR)) != 0) {
        // Generate a synthetic GfeResponseException letting the caller know
        // that they can't kill someone else's stream.
        throw GfeHttpResponseException(599, "");
    }
}

QVector<NvDisplayMode>
NvHTTP::getDisplayModeList(QString serverInfo)
{
    QXmlStreamReader xmlReader(serverInfo);
    QVector<NvDisplayMode> modes;

    while (!xmlReader.atEnd()) {
        while (xmlReader.readNextStartElement()) {
            auto name = xmlReader.name();
            if (XML_NAME_EQUALS(name, "DisplayMode")) {
                modes.append(NvDisplayMode());
            }
            else if (!modes.isEmpty()) {
                if (XML_NAME_EQUALS(name, "Width")) {
                    modes.last().width = xmlReader.readElementText().toInt();
                }
                else if (XML_NAME_EQUALS(name, "Height")) {
                    modes.last().height = xmlReader.readElementText().toInt();
                }
                else if (XML_NAME_EQUALS(name, "RefreshRate")) {
                    modes.last().refreshRate = xmlReader.readElementText().toInt();
                }
            }
        }
    }

    return modes;
}

QVector<NvApp>
NvHTTP::getAppList()
{
    QString appxml = openConnectionToString(m_BaseUrlHttps,
                                            "applist",
                                            nullptr,
                                            REQUEST_TIMEOUT_MS,
                                            NvLogLevel::NVLL_ERROR);
    verifyResponseStatus(appxml);

    QXmlStreamReader xmlReader(appxml);
    QVector<NvApp> apps;
    while (!xmlReader.atEnd()) {
        while (xmlReader.readNextStartElement()) {
            auto name = xmlReader.name();
            if (XML_NAME_EQUALS(name, "App")) {
                // We must have a valid app before advancing to the next one
                if (!apps.isEmpty() && !apps.last().isInitialized()) {
                    qWarning() << "Invalid applist XML";
                    throw std::runtime_error("Invalid applist XML");
                }
                apps.append(NvApp());
            }
            else if (!apps.isEmpty()) {
                if (XML_NAME_EQUALS(name, "AppTitle")) {
                    // If an app has no name, Sunshine may send us <AppTitle/>,
                    // which readElementText() returns as a null QString.
                    // We want to treat this as an empty QString instead, so we
                    // will explicitly convert it. An empty string will satisfy
                    // NvApp's isInitialized() check.
                    QString name = xmlReader.readElementText();
                    if (name.isNull()) {
                        name = "";
                    }
                    apps.last().name = name;
                }
                else if (XML_NAME_EQUALS(name, "ID")) {
                    apps.last().id = xmlReader.readElementText().toInt();
                }
                else if (XML_NAME_EQUALS(name, "IsHdrSupported")) {
                    apps.last().hdrSupported = xmlReader.readElementText() == "1";
                }
                else if (XML_NAME_EQUALS(name, "IsAppCollectorGame")) {
                    apps.last().isAppCollectorGame = xmlReader.readElementText() == "1";
                }
            }
        }
    }

    return apps;
}

void
NvHTTP::verifyResponseStatus(QString xml)
{
    QXmlStreamReader xmlReader(xml);

    while (xmlReader.readNextStartElement())
    {
        if (XML_NAME_EQUALS(xmlReader.name(), "root"))
        {
            // Status code can be 0xFFFFFFFF in some rare cases on GFE 3.20.3, and
            // QString::toInt() will fail in that case, so use QString::toUInt()
            // and cast the result to an int instead.
            int statusCode = (int)xmlReader.attributes().value("status_code").toUInt();
            if (statusCode == 200)
            {
                // Successful
                return;
            }
            else
            {
                QString statusMessage = xmlReader.attributes().value("status_message").toString();
                if (statusCode != 401) {
                    // 401 is expected for unpaired PCs when we fetch serverinfo over HTTPS
                    qWarning() << "Request failed:" << statusCode << statusMessage;
                }
                if (statusCode == -1 && statusMessage == "Invalid") {
                    // Special case handling an audio capture error which GFE doesn't
                    // provide any useful status message for.
                    statusCode = 418;
                    statusMessage = tr("Missing audio capture device. Reinstalling GeForce Experience should resolve this error.");
                }
                throw GfeHttpResponseException(statusCode, statusMessage);
            }
        }
    }

    throw GfeHttpResponseException(-1, "Malformed XML (missing root element)");
}

QImage
NvHTTP::getBoxArt(int appId)
{
    QNetworkReply* reply = openConnection(m_BaseUrlHttps,
                                          "appasset",
                                          "appid="+QString::number(appId)+
                                          "&AssetType=2&AssetIdx=0",
                                          REQUEST_TIMEOUT_MS,
                                          NvLogLevel::NVLL_VERBOSE);
    QImage image = QImageReader(reply).read();
    delete reply;

    return image;
}

QByteArray
NvHTTP::getXmlStringFromHex(QString xml,
                            QString tagName)
{
    return QByteArray::fromHex(getXmlString(xml, tagName).toUtf8());
}

QString
NvHTTP::getXmlString(QString xml,
                     QString tagName)
{
    QXmlStreamReader xmlReader(xml);

    while (!xmlReader.atEnd())
    {
        if (xmlReader.readNext() != QXmlStreamReader::StartElement)
        {
            continue;
        }

        if (xmlReader.name() == tagName)
        {
            return xmlReader.readElementText();
        }
    }

    return QString();
}

void NvHTTP::handleSslErrors(QNetworkReply* reply, const QList<QSslError>& errors)
{
    bool ignoreErrors = true;

    if (m_ServerCert.isNull()) {
        // We should never make an HTTPS request without a cert
        Q_ASSERT(!m_ServerCert.isNull());
        return;
    }

    for (const QSslError& error : errors) {
        if (m_ServerCert != error.certificate()) {
            ignoreErrors = false;
            break;
        }
    }

    if (ignoreErrors) {
        reply->ignoreSslErrors(errors);
    }
}

bool
NvHTTP::clipboardRequest(bool post, const QByteArray& body, QByteArray& response, QString& error)
{
    const char* method = post ? "POST" : "GET";
    if (m_ServerCert.isNull() || m_BaseUrlHttps.port(0) == 0) {
        error = "this host isn't paired over HTTPS";
        qWarning() << "[clipboard]" << method << "not sent:" << error
                   << "serverCertKnown=" << !m_ServerCert.isNull()
                   << "httpsPort=" << m_BaseUrlHttps.port(0)
                   << "host=" << m_BaseUrlHttps.host();
        return false;
    }

    QUrl url(m_BaseUrlHttps);
    url.setPath("/actions/clipboard");
    url.setQuery("type=text&uniqueid=" + (m_UseTrueUid ? IdentityManager::get()->getUniqueId() : "0123456789ABCDEF") +
                 "&uuid=" + QUuid::createUuid().toRfc4122().toHex());

    QNetworkRequest request(url);
    request.setSslConfiguration(IdentityManager::get()->getSslConfig());
    request.setHeader(QNetworkRequest::ContentTypeHeader, "text/plain; charset=utf-8");

#if QT_VERSION >= QT_VERSION_CHECK(6, 0, 0)
    request.setAttribute(QNetworkRequest::Http2AllowedAttribute, false);
#endif
#if QT_VERSION >= QT_VERSION_CHECK(6, 3, 0)
    request.setAttribute(QNetworkRequest::ConnectionCacheExpiryTimeoutSecondsAttribute, 0);
#endif

    // Never log the clipboard text itself or the request id; only sizes and outcomes.
    qInfo() << "[clipboard]" << method << url.scheme() + "://" + url.host() + ":" + QString::number(url.port())
                                       + url.path() + "?type=text"
            << "bodyBytes=" << body.size() << "qt=" << qVersion();
    QElapsedTimer clipTimer;
    clipTimer.start();

    QStringList sslProblems;
    auto sslLogConnection = connect(m_Nam, &QNetworkAccessManager::sslErrors, this,
                                    [&sslProblems](QNetworkReply*, const QList<QSslError>& errors) {
        for (const QSslError& e : errors) {
            sslProblems << e.errorString();
        }
    });
    auto sslErrorsConnection = connect(m_Nam, &QNetworkAccessManager::sslErrors, this, &NvHTTP::handleSslErrors);
    QNetworkReply* reply = post ? m_Nam->post(request, body) : m_Nam->get(request);

    QEventLoop loop;
    connect(reply, &QNetworkReply::finished, &loop, &QEventLoop::quit);
    connect(QCoreApplication::instance(), &QCoreApplication::aboutToQuit, &loop, &QEventLoop::quit);
    QTimer::singleShot(4000, &loop, &QEventLoop::quit);
    loop.exec(QEventLoop::ExcludeUserInputEvents);

    const bool timedOut = !reply->isFinished();
    if (timedOut) {
        reply->abort();
    }

#if QT_VERSION < QT_VERSION_CHECK(6, 3, 0)
    m_Nam->clearAccessCache();
#endif
    disconnect(sslErrorsConnection);
    disconnect(sslLogConnection);

    const int status = reply->attribute(QNetworkRequest::HttpStatusCodeAttribute).toInt();
    const QNetworkReply::NetworkError networkError = reply->error();
    const QString networkErrorText = reply->errorString();
    const QString responseType = reply->header(QNetworkRequest::ContentTypeHeader).toString();
    response = reply->readAll();
    delete reply;

    qInfo() << "[clipboard]" << method << "finished in" << clipTimer.elapsed() << "ms"
            << "timedOut=" << timedOut << "httpStatus=" << status
            << "networkError=" << (int)networkError << networkErrorText
            << "responseBytes=" << response.size() << "contentType=" << responseType
            << "sslProblemsIgnoredOrSeen=" << sslProblems;
    if (status >= 400 && !response.isEmpty() && response.size() < 400) {
        qWarning() << "[clipboard] host error body:" << QString::fromUtf8(response).simplified();
    }

    if (timedOut) {
        error = "the host didn't answer";
        qWarning() << "[clipboard] failed:" << error << "(4 s timeout; check the host is reachable on its HTTPS port)";
        return false;
    }
    if (status == 401 || status == 403) {
        error = (status == 401)
                ? "the host refused it. Allow clipboard access for this client in the host's web UI"
                : "the host says this client isn't streaming right now";
        return false;
    }
    if (networkError != QNetworkReply::NoError || (status != 0 && status >= 400)) {
        if (status == 404) {
            error = "this host doesn't support clipboard sync (it needs Vibepollo or another Apollo-style host)";
        }
        else {
            error = "request failed (" + QString::number(status != 0 ? status : (int)networkError) + ")";
        }
        return false;
    }

    return true;
}

bool
NvHTTP::getMonitors(QStringList& ids, QStringList& names, QString& error)
{
    ids.clear();
    names.clear();
    if (m_ServerCert.isNull() || m_BaseUrlHttps.port(0) == 0) {
        error = "this host isn't paired over HTTPS";
        return false;
    }

    QUrl url(m_BaseUrlHttps);
    url.setPath("/vibebeam/monitors");
    url.setQuery("uniqueid=" + (m_UseTrueUid ? IdentityManager::get()->getUniqueId() : "0123456789ABCDEF") +
                 "&uuid=" + QUuid::createUuid().toRfc4122().toHex());

    QNetworkRequest request(url);
    request.setSslConfiguration(IdentityManager::get()->getSslConfig());
#if QT_VERSION >= QT_VERSION_CHECK(6, 0, 0)
    request.setAttribute(QNetworkRequest::Http2AllowedAttribute, false);
#endif
#if QT_VERSION >= QT_VERSION_CHECK(6, 3, 0)
    request.setAttribute(QNetworkRequest::ConnectionCacheExpiryTimeoutSecondsAttribute, 0);
#endif

    auto sslErrorsConnection = connect(m_Nam, &QNetworkAccessManager::sslErrors, this, &NvHTTP::handleSslErrors);
    QNetworkReply* reply = m_Nam->get(request);

    QEventLoop loop;
    connect(reply, &QNetworkReply::finished, &loop, &QEventLoop::quit);
    connect(QCoreApplication::instance(), &QCoreApplication::aboutToQuit, &loop, &QEventLoop::quit);
    QTimer::singleShot(5000, &loop, &QEventLoop::quit);
    loop.exec(QEventLoop::ExcludeUserInputEvents);

    const bool timedOut = !reply->isFinished();
    if (timedOut) {
        reply->abort();
    }
#if QT_VERSION < QT_VERSION_CHECK(6, 3, 0)
    m_Nam->clearAccessCache();
#endif
    disconnect(sslErrorsConnection);

    const int status = reply->attribute(QNetworkRequest::HttpStatusCodeAttribute).toInt();
    const QByteArray body = reply->readAll();
    delete reply;

    if (timedOut) {
        error = "the host didn't answer";
        return false;
    }
    if (status == 404) {
        error = "this host doesn't offer a monitor choice (it needs Vibebeam)";
        return false;
    }
    if (status == 401 || status == 403) {
        error = "the host refused the request";
        return false;
    }
    if (status != 200) {
        error = "request failed (" + QString::number(status) + ")";
        return false;
    }

    // <root status_code="200"><monitor><id>..</id><name>..</name></monitor>...</root>
    QXmlStreamReader xml(body);
    QString id, name;
    bool inMonitor = false;
    while (!xml.atEnd()) {
        xml.readNext();
        if (xml.isStartElement()) {
            if (xml.name() == QLatin1String("monitor")) {
                inMonitor = true;
                id.clear();
                name.clear();
            }
            else if (inMonitor && xml.name() == QLatin1String("id")) {
                id = xml.readElementText();
            }
            else if (inMonitor && xml.name() == QLatin1String("name")) {
                name = xml.readElementText();
            }
        }
        else if (xml.isEndElement() && xml.name() == QLatin1String("monitor")) {
            inMonitor = false;
            if (!id.isEmpty()) {
                ids << id;
                names << (name.isEmpty() ? id : name);
            }
        }
    }
    if (xml.hasError()) {
        error = "the host's answer couldn't be read";
        return false;
    }
    return true;
}

bool
NvHTTP::setRuntimeBitrate(int bitrateKbps, int& appliedKbps, QString& error)
{
    appliedKbps = 0;
    if (m_ServerCert.isNull() || m_BaseUrlHttps.port(0) == 0) {
        error = "this host isn't paired over HTTPS";
        return false;
    }

    QUrl url(m_BaseUrlHttps);
    url.setPath("/bitrate");
    url.setQuery("uniqueid=" + (m_UseTrueUid ? IdentityManager::get()->getUniqueId() : "0123456789ABCDEF") +
                 "&uuid=" + QUuid::createUuid().toRfc4122().toHex() +
                 "&bitrate=" + QString::number(bitrateKbps));

    QNetworkRequest request(url);
    request.setSslConfiguration(IdentityManager::get()->getSslConfig());
#if QT_VERSION >= QT_VERSION_CHECK(6, 0, 0)
    request.setAttribute(QNetworkRequest::Http2AllowedAttribute, false);
#endif
#if QT_VERSION >= QT_VERSION_CHECK(6, 3, 0)
    request.setAttribute(QNetworkRequest::ConnectionCacheExpiryTimeoutSecondsAttribute, 0);
#endif

    auto sslErrorsConnection = connect(m_Nam, &QNetworkAccessManager::sslErrors, this, &NvHTTP::handleSslErrors);
    QNetworkReply* reply = m_Nam->get(request);

    QEventLoop loop;
    connect(reply, &QNetworkReply::finished, &loop, &QEventLoop::quit);
    connect(QCoreApplication::instance(), &QCoreApplication::aboutToQuit, &loop, &QEventLoop::quit);
    QTimer::singleShot(4000, &loop, &QEventLoop::quit);
    loop.exec(QEventLoop::ExcludeUserInputEvents);

    const bool timedOut = !reply->isFinished();
    if (timedOut) {
        reply->abort();
    }
#if QT_VERSION < QT_VERSION_CHECK(6, 3, 0)
    m_Nam->clearAccessCache();
#endif
    disconnect(sslErrorsConnection);

    const int status = reply->attribute(QNetworkRequest::HttpStatusCodeAttribute).toInt();
    const QByteArray body = reply->readAll();
    delete reply;

    if (timedOut) {
        error = "the host didn't answer";
        return false;
    }
    if (status == 401 || status == 403) {
        error = "the host refused it";
        return false;
    }
    if (status == 404) {
        error = "this host can't change the bitrate while streaming";
        return false;
    }
    if (status != 200) {
        error = "request failed (" + QString::number(status) + ")";
        return false;
    }

    // The host answers with XML such as <root status_code="200"><bitrate>30000</bitrate></root>;
    // a refused change still comes back as HTTP 200 with a different status_code
    const QString text = QString::fromUtf8(body);
    QRegularExpressionMatch code = QRegularExpression("status_code=\"(\\d+)\"").match(text);
    if (code.hasMatch() && code.captured(1).toInt() != 200) {
        error = code.captured(1).toInt() == 404 ? "no active stream for this device" : "the host refused the new bitrate";
        return false;
    }
    QRegularExpressionMatch applied = QRegularExpression("<bitrate>(\\d+)</bitrate>").match(text);
    appliedKbps = applied.hasMatch() ? applied.captured(1).toInt() : bitrateKbps;
    return true;
}

bool
NvHTTP::uploadFile(const QString& localPath, QString& savedName, QString& error,
                   std::atomic<bool>* cancelled, std::atomic<int>* progress)
{
    static const qint64 kMaxUploadBytes = 512LL * 1024 * 1024; // matches the host's limit

    QFileInfo info(localPath);
    if (!info.exists() || !info.isFile()) {
        error = info.isDir() ? "folders can't be sent yet, send the files inside" : "the file no longer exists";
        qWarning() << "[file] not sent:" << error << "path=" << localPath;
        return false;
    }
    if (info.size() > kMaxUploadBytes) {
        error = "it's larger than 512 MB";
        qWarning() << "[file] not sent:" << error << "size=" << info.size();
        return false;
    }
    if (m_ServerCert.isNull() || m_BaseUrlHttps.port(0) == 0) {
        error = "this host isn't paired over HTTPS";
        qWarning() << "[file] not sent:" << error << "host=" << m_BaseUrlHttps.host();
        return false;
    }

    QFile file(localPath);
    if (!file.open(QIODevice::ReadOnly)) {
        error = "couldn't read the file (" + file.errorString() + ")";
        qWarning() << "[file] not sent:" << error;
        return false;
    }
    // Keep the QFile open for the duration of the request. QNetworkAccessManager
    // reads directly from this device instead of retaining a second, potentially
    // 512 MiB, QByteArray in the client.
    const qint64 uploadBytes = file.size();
    if (uploadBytes != info.size() || uploadBytes < 0 || uploadBytes > kMaxUploadBytes) {
        error = "the file changed size while preparing the upload; try again";
        qWarning() << "[file] not sent:" << error << "path=" << localPath;
        return false;
    }

    QUrl url(m_BaseUrlHttps);
    url.setPath("/actions/upload");
    QUrlQuery query;
    query.addQueryItem("name", QString::fromLatin1(QUrl::toPercentEncoding(info.fileName())));
    const QString uniqueId = m_UseTrueUid ? IdentityManager::get()->getUniqueId() : QStringLiteral("0123456789ABCDEF");
    query.addQueryItem("uniqueid", uniqueId);
    query.addQueryItem("uuid", QString::fromLatin1(QUuid::createUuid().toRfc4122().toHex()));
    url.setQuery(query);

    QNetworkRequest request(url);
    request.setSslConfiguration(IdentityManager::get()->getSslConfig());
    request.setHeader(QNetworkRequest::ContentTypeHeader, "application/octet-stream");
    // The host expects a fixed-size request body. Supplying Content-Length lets
    // Qt stream from a seekable QFile without buffering the entire upload.
    request.setHeader(QNetworkRequest::ContentLengthHeader, QVariant::fromValue<qint64>(uploadBytes));
    request.setAttribute(QNetworkRequest::DoNotBufferUploadDataAttribute, true);
#if QT_VERSION >= QT_VERSION_CHECK(6, 0, 0)
    request.setAttribute(QNetworkRequest::Http2AllowedAttribute, false);
#endif
#if QT_VERSION >= QT_VERSION_CHECK(6, 3, 0)
    request.setAttribute(QNetworkRequest::ConnectionCacheExpiryTimeoutSecondsAttribute, 0);
#endif

    qInfo() << "[file] uploading" << info.fileName() << "bytes=" << uploadBytes
            << "to" << url.host() + ":" + QString::number(url.port()) << "/actions/upload";
    QElapsedTimer timer;
    timer.start();

    QStringList sslProblems;
    auto sslLogConnection = connect(m_Nam, &QNetworkAccessManager::sslErrors, this,
                                    [&sslProblems](QNetworkReply*, const QList<QSslError>& errors) {
        for (const QSslError& e : errors) {
            sslProblems << e.errorString();
        }
    });
    auto sslErrorsConnection = connect(m_Nam, &QNetworkAccessManager::sslErrors, this, &NvHTTP::handleSslErrors);
    // QFile and QNetworkAccessManager live on the same worker thread. The file
    // remains open until the reply has finished or has been aborted/deleted.
    QNetworkReply* reply = m_Nam->post(request, &file);
    if (progress) {
        progress->store(0);
        connect(reply, &QNetworkReply::uploadProgress, reply, [progress](qint64 sent, qint64 total) {
            if (total > 0) progress->store(static_cast<int>(qBound<qint64>(0, sent * 100 / total, 99)));
        });
    }
    QTimer cancelPoll;
    if (cancelled) {
        cancelPoll.setInterval(125);
        connect(&cancelPoll, &QTimer::timeout, reply, [reply, cancelled]() {
            if (cancelled->load() && !reply->isFinished()) reply->abort();
        });
        cancelPoll.start();
    }

    QEventLoop loop;
    connect(reply, &QNetworkReply::finished, &loop, &QEventLoop::quit);
    // Give up after 10 minutes, or sooner if the app is quitting
    connect(QCoreApplication::instance(), &QCoreApplication::aboutToQuit, &loop, &QEventLoop::quit);
    QTimer::singleShot(10 * 60 * 1000, &loop, &QEventLoop::quit);
    loop.exec(QEventLoop::ExcludeUserInputEvents);

    const bool timedOut = !reply->isFinished();
    if (timedOut) {
        reply->abort();
    }

#if QT_VERSION < QT_VERSION_CHECK(6, 3, 0)
    m_Nam->clearAccessCache();
#endif
    disconnect(sslErrorsConnection);
    disconnect(sslLogConnection);

    const int status = reply->attribute(QNetworkRequest::HttpStatusCodeAttribute).toInt();
    const QNetworkReply::NetworkError networkError = reply->error();
    const QString networkErrorText = reply->errorString();
    const QByteArray response = reply->readAll();
    delete reply;

    qInfo() << "[file] upload finished in" << timer.elapsed() << "ms"
            << "timedOut=" << timedOut << "httpStatus=" << status
            << "networkError=" << (int)networkError << networkErrorText
            << "responseBytes=" << response.size() << "sslProblems=" << sslProblems;

    if (cancelled && cancelled->load()) {
        error = "transfer canceled";
    }
    else if (timedOut) {
        error = "the host didn\'t finish receiving it";
    }
    else if (status == 401) {
        error = "the host refused it. Allow \"Upload files\" for this client in the host's web UI";
    }
    else if (status == 403) {
        error = "the host says this client isn't streaming right now";
    }
    else if (status == 404 || status == 405) {
        error = "this host doesn't support file transfer (it needs Vibebeam)";
    }
    else if (status == 413) {
        error = "the host says it's too large";
    }
    else if (networkError != QNetworkReply::NoError || (status != 0 && status >= 400)) {
        error = "request failed (" + QString::number(status != 0 ? status : (int)networkError) + ")";
    }
    else {
        savedName = QString::fromUtf8(response).trimmed();
        if (savedName.isEmpty()) {
            savedName = info.fileName();
        }
        return true;
    }

    qWarning() << "[file] failed:" << error;
    return false;
}


bool NvHTTP::downloadNewestHostFile(QString& savedPath, QString& error)
{
    return downloadHostFile(QString(), savedPath, error);
}

bool NvHTTP::getHostFileList(QStringList& names, QString& error)
{
    if (m_ServerCert.isNull() || m_BaseUrlHttps.port(0) == 0) {
        error = "this host isn't paired over HTTPS";
        return false;
    }
    QUrl url(m_BaseUrlHttps);
    url.setPath("/actions/files");
    QNetworkRequest request(url);
    request.setSslConfiguration(IdentityManager::get()->getSslConfig());
    request.setAttribute(QNetworkRequest::RedirectPolicyAttribute, QNetworkRequest::ManualRedirectPolicy);
    auto ssl = connect(m_Nam, &QNetworkAccessManager::sslErrors, this, &NvHTTP::handleSslErrors);
    QNetworkReply* reply = m_Nam->get(request);
    QEventLoop loop;
    connect(reply, &QNetworkReply::finished, &loop, &QEventLoop::quit);
    QTimer timer;
    timer.setSingleShot(true);
    connect(&timer, &QTimer::timeout, &loop, &QEventLoop::quit);
    timer.start(15000);
    loop.exec(QEventLoop::ExcludeUserInputEvents);
    const bool timeout = !reply->isFinished();
    if (timeout) reply->abort();
    const int status = reply->attribute(QNetworkRequest::HttpStatusCodeAttribute).toInt();
    const bool success = !timeout && status == 200 && reply->error() == QNetworkReply::NoError;
    const QByteArray data = reply->read(128 * 1024 + 1);
    delete reply;
    disconnect(ssl);
    if (!success) {
        error = status == 401 ? "enable Download files for this client in Vibebeam" :
                status == 403 ? "a stream must be active to download host files" :
                status == 404 ? "the host needs the matching download beta" :
                timeout ? "file list request timed out" :
                "host file list failed (HTTP " + QString::number(status) + ")";
        return false;
    }
    if (data.size() > 128 * 1024) {
        error = "host file list was too large";
        return false;
    }
    QJsonParseError parseError;
    const QJsonDocument document = QJsonDocument::fromJson(data, &parseError);
    if (parseError.error != QJsonParseError::NoError || !document.isObject()) {
        error = "invalid host file list";
        return false;
    }
    names.clear();
    const auto entries = document.object().value("files").toArray();
    for (const auto& entry : entries) {
        const QString name = entry.toObject().value("name").toString();
        if (!name.isEmpty() && name != "." && name != ".." &&
            name == QFileInfo(name).fileName() &&
            !name.contains('/') && !name.contains('\\') && !names.contains(name)) {
            names.append(name);
        }
    }
    return true;
}

bool NvHTTP::downloadHostFile(const QString& requestedName, QString& savedPath, QString& error,
                              std::atomic<bool>* cancelled, std::atomic<int>* progress)
{
    constexpr qint64 maxDownload = 512LL * 1024 * 1024;
    if (m_ServerCert.isNull() || m_BaseUrlHttps.port(0) == 0) {
        error = "this host isn't paired over HTTPS";
        return false;
    }

    // Listing is deliberately bounded on the host. Keep the same pinned TLS
    // configuration used for file uploads, and never follow redirects.
    QUrl listUrl(m_BaseUrlHttps);
    listUrl.setPath("/actions/files");
    QNetworkRequest listRequest(listUrl);
    listRequest.setSslConfiguration(IdentityManager::get()->getSslConfig());
    listRequest.setAttribute(QNetworkRequest::RedirectPolicyAttribute, QNetworkRequest::ManualRedirectPolicy);
    auto sslHandler = connect(m_Nam, &QNetworkAccessManager::sslErrors,
                              this, &NvHTTP::handleSslErrors);
    QNetworkReply* listReply = m_Nam->get(listRequest);
    QEventLoop listLoop;
    connect(listReply, &QNetworkReply::finished, &listLoop, &QEventLoop::quit);
    QTimer listTimer;
    listTimer.setSingleShot(true);
    connect(&listTimer, &QTimer::timeout, &listLoop, &QEventLoop::quit);
    listTimer.start(15000);
    listLoop.exec(QEventLoop::ExcludeUserInputEvents);
    const bool listTimedOut = !listReply->isFinished();
    if (listTimedOut) listReply->abort();
    const int listStatus = listReply->attribute(QNetworkRequest::HttpStatusCodeAttribute).toInt();
    const bool listOk = !listTimedOut && listReply->error() == QNetworkReply::NoError &&
                        listStatus == 200;
    const QByteArray listing = listReply->readAll();
    delete listReply;
    if (!listOk) {
        disconnect(sslHandler);
        error = listStatus == 401 ? "allow Download files for this client in the Vibebeam web UI" :
                listStatus == 403 ? "host-to-client downloads require an active stream" :
                listStatus == 404 ? "the host doesn't support downloads yet; install the host beta" :
                listTimedOut ? "timed out asking the host for files" :
                "couldn't list host files (HTTP " + QString::number(listStatus) + ")";
        return false;
    }
    if (listing.size() > 128 * 1024) {
        disconnect(sslHandler);
        error = "the host returned an oversized file list";
        return false;
    }
    const QJsonDocument parsed = QJsonDocument::fromJson(listing);
    const QJsonArray files = parsed.object().value("files").toArray();
    if (files.isEmpty()) {
        disconnect(sslHandler);
        error = "the host's shared transfer folder has no downloadable files";
        return false;
    }
    QJsonObject selected;
    if (requestedName.isEmpty()) {
        selected = files.first().toObject();
    }
    else {
        for (const auto& candidate : files) {
            const QJsonObject entry = candidate.toObject();
            if (entry.value("name").toString() == requestedName) {
                selected = entry;
                break;
            }
        }
        if (selected.isEmpty()) {
            disconnect(sslHandler);
            error = "the selected host file is no longer available";
            return false;
        }
    }
    const QString name = selected.value("name").toString();
    const qint64 expectedSize = static_cast<qint64>(selected.value("size").toDouble(-1));
    if (name.isEmpty() || name == "." || name == ".." ||
        name != QFileInfo(name).fileName() || name.contains('\\') || name.contains('/') ||
        expectedSize < 0 || expectedSize > maxDownload) {
        disconnect(sslHandler);
        error = "the host returned an invalid download entry";
        return false;
    }

    QString directory = QStandardPaths::writableLocation(QStandardPaths::DownloadLocation);
    if (directory.isEmpty()) {
        directory = QDir::homePath() + "/Downloads";
    }
    directory = QDir(directory).filePath("Vibelight");
    if (!QDir().mkpath(directory)) {
        disconnect(sslHandler);
        error = "couldn't create Downloads/Vibelight";
        return false;
    }
    QTemporaryFile temporary(QDir(directory).filePath(".vibelight-XXXXXX.part"));
    if (!temporary.open()) {
        disconnect(sslHandler);
        error = "couldn't create a temporary download file";
        return false;
    }

    QUrl url(m_BaseUrlHttps);
    url.setPath("/actions/download");
    QUrlQuery query;
    query.addQueryItem("name", name);
    url.setQuery(query);
    QNetworkRequest request(url);
    request.setSslConfiguration(IdentityManager::get()->getSslConfig());
    request.setAttribute(QNetworkRequest::RedirectPolicyAttribute, QNetworkRequest::ManualRedirectPolicy);
    request.setRawHeader("Accept-Encoding", "identity");
    QNetworkReply* reply = m_Nam->get(request);
    qint64 received = 0;
    bool writeFailed = false;
    if (progress) progress->store(0);
    QTimer cancelPoll;
    if (cancelled) {
        cancelPoll.setInterval(125);
        connect(&cancelPoll, &QTimer::timeout, reply, [reply, cancelled]() {
            if (cancelled->load() && !reply->isFinished()) reply->abort();
        });
        cancelPoll.start();
    }
    auto drain = [&]() {
        while (reply->bytesAvailable() > 0) {
            const QByteArray chunk = reply->read(qMin<qint64>(reply->bytesAvailable(), 128 * 1024));
            if (chunk.isEmpty()) break;
            received += chunk.size();
            if (progress && expectedSize > 0)
                progress->store(static_cast<int>(qBound<qint64>(0, received * 100 / expectedSize, 99)));
            if (received > maxDownload || received > expectedSize ||
                temporary.write(chunk) != chunk.size()) {
                writeFailed = true;
                reply->abort();
                return;
            }
        }
    };
    connect(reply, &QNetworkReply::readyRead, reply, drain);
    QEventLoop loop;
    connect(reply, &QNetworkReply::finished, &loop, &QEventLoop::quit);
    QTimer timer;
    timer.setSingleShot(true);
    connect(&timer, &QTimer::timeout, &loop, &QEventLoop::quit);
    timer.start(10 * 60 * 1000);
    loop.exec(QEventLoop::ExcludeUserInputEvents);
    const bool timedOut = !reply->isFinished();
    if (timedOut) reply->abort();
    if (!writeFailed) drain();
    const int status = reply->attribute(QNetworkRequest::HttpStatusCodeAttribute).toInt();
    const bool complete = !timedOut && !writeFailed &&
                          reply->error() == QNetworkReply::NoError &&
                          status == 200 && received == expectedSize &&
                          reply->header(QNetworkRequest::ContentLengthHeader).toLongLong() == expectedSize;
    delete reply;
    disconnect(sslHandler);
    if (!complete || !temporary.flush() || (cancelled && cancelled->load())) {
        error = (cancelled && cancelled->load()) ? "transfer canceled" :
                status == 401 ? "Download files permission is required" :
                status == 403 ? "the stream must remain active during download" :
                timedOut ? "host download timed out" :
                "download failed or was incomplete";
        return false;
    }
    temporary.close();

    // Never overwrite an existing local file. QTemporaryFile::rename refuses
    // to replace an existing target, unlike QSaveFile's commit behavior.
    const QFileInfo original(name);
    for (int index = 0; index < 1000; ++index) {
        QString candidate = name;
        if (index > 0) {
            const QString stem = original.completeBaseName();
            const QString ext = original.completeSuffix();
            candidate = stem + " (" + QString::number(index) + ")" +
                        (ext.isEmpty() ? QString() : "." + ext);
        }
        const QString dest = QDir(directory).filePath(candidate);
        if (QFileInfo::exists(dest)) continue;
        if (temporary.rename(dest)) {
            savedPath = dest;
            if (progress) progress->store(100);
            return true;
        }
        if (QFileInfo::exists(dest)) continue; // concurrent download won the name
        break;
    }
    error = "couldn't save the received file without overwriting another file";
    return false;
}

bool
NvHTTP::getClipboardText(QString& text, QString& error)
{
    QByteArray response;
    if (!clipboardRequest(false, QByteArray(), response, error)) {
        return false;
    }
    text = QString::fromUtf8(response);
    return true;
}

bool
NvHTTP::setClipboardText(const QString& text, QString& error)
{
    QByteArray response;
    return clipboardRequest(true, text.toUtf8(), response, error);
}

QString
NvHTTP::openConnectionToString(QUrl baseUrl,
                               QString command,
                               QString arguments,
                               int timeoutMs,
                               NvLogLevel logLevel)
{
    QNetworkReply* reply = openConnection(baseUrl, command, arguments, timeoutMs, logLevel);
    QString ret;

    QTextStream stream(reply);

#if QT_VERSION >= QT_VERSION_CHECK(6, 0, 0)
    stream.setEncoding(QStringConverter::Utf8);
#else
    stream.setCodec("UTF-8");
#endif

    ret = stream.readAll();
    delete reply;

    return ret;
}

QNetworkReply*
NvHTTP::openConnection(QUrl baseUrl,
                       QString command,
                       QString arguments,
                       int timeoutMs,
                       NvLogLevel logLevel)
{
    // Port must be set
    Q_ASSERT(baseUrl.port(0) != 0);

    // Build a URL for the request
    QUrl url(baseUrl);
    url.setPath("/" + command);

    // Use a placeholder UID for GFE allow them to quit games for each other.
    url.setQuery("uniqueid=" + (m_UseTrueUid ? IdentityManager::get()->getUniqueId() : "0123456789ABCDEF") +
                 "&uuid=" + QUuid::createUuid().toRfc4122().toHex() +
                 ((arguments != nullptr) ? ("&" + arguments) : ""));

    QNetworkRequest request(url);

    // Add our client certificate
    request.setSslConfiguration(IdentityManager::get()->getSslConfig());

#if QT_VERSION >= QT_VERSION_CHECK(6, 0, 0)
    // Disable HTTP/2 (GFE 3.22 doesn't like it) and Qt 6 enables it by default
    request.setAttribute(QNetworkRequest::Http2AllowedAttribute, false);
#endif

#if QT_VERSION >= QT_VERSION_CHECK(6, 3, 0)
    // Use fine-grained idle timeouts to avoid calling QNetworkAccessManager::clearAccessCache(),
    // which tears down the NAM's global thread each time. We must not keep persistent connections
    // or GFE will puke.
    request.setAttribute(QNetworkRequest::ConnectionCacheExpiryTimeoutSecondsAttribute, 0);
#endif

    auto sslErrorsConnection = connect(m_Nam, &QNetworkAccessManager::sslErrors, this, &NvHTTP::handleSslErrors);
    QNetworkReply* reply = m_Nam->get(request);

    // Run the request with a timeout if requested
    QEventLoop loop;
    connect(reply, &QNetworkReply::finished, &loop, &QEventLoop::quit);
    connect(QCoreApplication::instance(), &QCoreApplication::aboutToQuit, &loop, &QEventLoop::quit);
    if (timeoutMs) {
        QTimer::singleShot(timeoutMs, &loop, &QEventLoop::quit);
    }
    if (logLevel >= NvLogLevel::NVLL_VERBOSE) {
        qInfo() << "Executing request:" << url.toString();
    }
    loop.exec(QEventLoop::ExcludeUserInputEvents);

    // Abort the request if it timed out
    if (!reply->isFinished())
    {
        if (logLevel >= NvLogLevel::NVLL_ERROR) {
            qWarning() << "Aborting timed out request for" << url.toString();
        }
        reply->abort();
    }

#if QT_VERSION < QT_VERSION_CHECK(6, 3, 0)
    // If we couldn't use fine-grained connection idle timeouts, kill them all now
    m_Nam->clearAccessCache();
#endif
    disconnect(sslErrorsConnection);

    // Handle error
    if (reply->error() != QNetworkReply::NoError)
    {
        if (logLevel >= NvLogLevel::NVLL_ERROR) {
            qWarning() << command << "request failed with error:" << reply->error();
        }

        if (reply->error() == QNetworkReply::SslHandshakeFailedError) {
            // This will trigger falling back to HTTP for the serverinfo query
            // then pairing again to get the updated certificate.
            GfeHttpResponseException exception(401, "Server certificate mismatch");
            delete reply;
            throw exception;
        }
        else if (reply->error() == QNetworkReply::OperationCanceledError) {
            QtNetworkReplyException exception(QNetworkReply::TimeoutError, "Request timed out");
            delete reply;
            throw exception;
        }
        else {
            QtNetworkReplyException exception(reply->error(), reply->errorString());
            delete reply;
            throw exception;
        }
    }

    return reply;
}
