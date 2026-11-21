import QtQuick 2.9
import QtQuick.Controls 2.2
import QtTest 1.2
import "../../app/gui"

Item {
    id: window
    width: 900
    height: 650
    StackView { id: stackView; anchors.fill: parent }
    Component { id: pcPage; PcView {} }

    TestCase {
        name: "HostSelectionTwoFactor"
        when: windowShown
        function init() {
            stackView.push(pcPage, {}, StackView.Immediate)
            tryCompare(stackView, "depth", 1)
        }
        function cleanup() {
            stackView.clear(StackView.Immediate)
            wait(20)
        }
        function test_codeNeededBeforeOpeningAppList() {
            var pc = stackView.currentItem
            pc.selectHost(0, "Home", false)
            compare(stackView.depth, 1)
            compare(pc.computerModel.checkCount, 1)
            compare(pc.computerModel.lastCode, "")
            pc.computerModel.deliver(470, "")
            compare(stackView.depth, 1)
            verify(pc.hostAuthPending)
            var dialog = findChild(pc, "hostTwoFactorDialog")
            tryCompare(dialog, "visible", true)
            dialog.reject()
            compare(pc.hostAuthPending, false)
            compare(stackView.depth, 1)
            compare(pc.computerModel.cancelCount, 1)
        }
        function test_successOpensAppGridWithoutSecondChallenge() {
            var pc = stackView.currentItem
            pc.selectHost(0, "Home", false)
            compare(stackView.depth, 1)
            pc.computerModel.deliver(200, "")
            tryCompare(stackView, "depth", 2)
            compare(stackView.currentItem.objectName, "Home")
            compare(pc.computerModel.checkCount, 1)
        }
        function test_wrongCodeStaysOnComputersAndCanRetry() {
            var pc = stackView.currentItem
            pc.selectHost(0, "Home", false)
            pc.computerModel.deliver(470, "")
            var dialog = findChild(pc, "hostTwoFactorDialog")
            tryCompare(dialog, "visible", true)
            var field = findChild(pc, "hostTwoFactorCode")
            field.text = "123456"
            dialog.accept()
            compare(pc.computerModel.lastCode, "123456")
            compare(stackView.depth, 1)
            pc.computerModel.deliver(471, "")
            tryCompare(dialog, "visible", true)
            compare(stackView.depth, 1)
            dialog.reject()
        }
        function test_lockoutAndCancellationNeverNavigate() {
            var pc = stackView.currentItem
            pc.selectHost(0, "Home", false)
            pc.computerModel.deliver(472, "")
            compare(stackView.depth, 1)
            verify(!pc.hostAuthPending)
        }
        function test_legacyHostKeepsOldLaunchFlow() {
            var pc = stackView.currentItem
            pc.computerModel.supportsPreflight = false
            pc.selectHost(0, "Home", false)
            tryCompare(stackView, "depth", 2)
            compare(pc.computerModel.checkCount, 0)
        }
    }
}
