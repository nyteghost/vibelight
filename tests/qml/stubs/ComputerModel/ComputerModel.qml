import QtQuick 2.9

ListModel {
    signal pairingCompleted(var error)
    signal connectionTestCompleted(int result, string blockedPorts)
    signal monitorsReady(int computerIndex, var ids, var names, string current, string error)
    signal twoFactorCheckCompleted(int computerIndex, int status, string error)
    property bool supportsPreflight: true
    property int checkCount: 0
    property int cancelCount: 0
    property string lastCode: ""
    ListElement {
        name: "Home"
        online: true
        paired: true
        serverSupported: true
        statusUnknown: false
        wakeable: false
        busy: false
        profileSummary: "Global defaults"
        details: "Test host"
    }
    function initialize(manager) {}
    function supportsHostTwoFactorPreflight(index) { return supportsPreflight && index === 0 }
    function checkTwoFactorForComputer(index, code) { checkCount++; lastCode = code }
    function cancelTwoFactorForComputer(index) { cancelCount++ }
    function deliver(status, error) { twoFactorCheckCompleted(0, status, error || "") }
    function profileForComputer(index) { return { uuid: "test-host", name: "Home", monitor: "", enabled: false,
        width: 1920, height: 1080, fps: 60, bitrateKbps: 20000, adaptiveBitrate: true } }
    function chooseMonitorForComputer(index) {}
    function generatePinString() { return "1234" }
    function pairComputer(index, pin) {}
    function testConnectionForComputer(index) {}
    function wakeComputer(index) {}
    function renameComputer(index, name) {}
    function deleteComputer(index) {}
}
