import QtQuick 2.9
import Session 1.0
ListModel {
    signal computerLost()
    property Component sessionFactory: Component { Session {} }
    ListElement { name: "Desktop"; appid: 1; boxart: ""; running: false; hidden: false; isAppCollectorGame: false; directLaunch: false }
    function initialize(manager, index, hidden) {}
    function getDirectLaunchAppIndex() { return -1 }
    function getRunningAppId() { return 0 }
    function getRunningAppName() { return "" }
    function setAppDirectLaunch(index, enabled) { setProperty(index, "directLaunch", enabled) }
    function setAppHidden(index, enabled) { setProperty(index, "hidden", enabled) }
    function createSessionForApp(index) { return sessionFactory.createObject(null) }
}
