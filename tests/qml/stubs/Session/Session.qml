import QtQuick 2.9
import Session 1.0
QtObject {
    signal stageStarting(string stage)
    signal stageFailed(string stage, int code, string ports)
    signal connectionStarted()
    signal displayLaunchError(string text)
    signal twoFactorNeeded(string message)
    signal quitStarting()
    signal sessionFinished(int result)
    signal readyForDeletion()
    property var launchWarnings: []
    function initialize(window) { SessionTestState.initializations++; return true }
    function start() { SessionTestState.starts++ }
    function wantsReconnect() { return false }
    function submitTwoFactorCode(code) {}
    function cancelTwoFactor() {}
}
