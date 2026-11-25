import QtQuick 2.9
import QtQuick.Window 2.2
import QtTest 1.2
import "../../app/gui"

Item {
    id: stackView
    width: 640
    height: 480
    QtObject {
        id: storage
        property int saves: 0
        property var savedValues
        property string savedHost: ""
        property bool savedEnabled: false
        property string failure: ""
        function saveProfileForComputer(uuid, values, enabled) {
            saves++
            savedHost = uuid
            savedValues = values
            savedEnabled = enabled
            return failure
        }
    }
    HostProfileDialog { id: editor; profileModel: storage }
    TestCase {
        name: "HostProfileEditor"
        when: windowShown
        function profile() {
            return { uuid: "host-A", name: "My PC", monitor: "", enabled: true, invalid: false,
                width: 1920, height: 1080, fps: 60, bitrateKbps: 20000, adaptiveBitrate: true }
        }
        function init() {
            storage.saves = 0
            storage.failure = ""
            stackView.Window.window.width = 640
            stackView.Window.window.height = 480
            editor.edit(profile())
            tryCompare(editor, "visible", true)
        }
        function cleanup() {
            editor.close()
            tryCompare(editor, "visible", false)
        }
        function test_cancelDoesNotSave() {
            editor.reject()
            tryCompare(editor, "visible", false)
            compare(storage.saves, 0)
        }
        function test_typedValueIsSavedWithoutEnter() {
            var field = findChild(editor, "profileFrameRate")
            verify(field !== null)
            field.contentItem.text = "120"
            editor.saveProfile()
            tryCompare(editor, "visible", false)
            compare(storage.saves, 1)
            compare(storage.savedHost, "host-A")
            compare(storage.savedValues.fps, 120)
            compare(storage.savedValues.bitrateKbps, 20000)
            verify(storage.savedEnabled)
        }
        function test_badInputStaysOpenWithoutWrite() {
            var field = findChild(editor, "profileFrameRate")
            field.contentItem.text = "not a number"
            editor.saveProfile()
            compare(storage.saves, 0)
            verify(editor.visible)
            verify(editor.problem.length > 0)
        }
        function test_storageFailureKeepsDraft() {
            storage.failure = "Storage unavailable"
            editor.saveProfile()
            verify(editor.visible)
            compare(editor.problem, storage.failure)
        }
        function test_narrowShortWindowKeepsSaveReachable() {
            stackView.Window.window.width = 320
            stackView.Window.window.height = 360
            wait(50)
            verify(editor.width <= stackView.Window.window.width)
            verify(editor.height <= stackView.Window.window.height)
            verify(editor.footer !== null)
            editor.saveProfile()
            tryCompare(editor, "visible", false)
            compare(storage.saves, 1)
        }
    }
}
