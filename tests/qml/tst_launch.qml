import QtQuick 2.9
import QtQuick.Controls 2.2
import QtQuick.Window 2.2
import QtTest 1.2
import Session 1.0
import AppModel 1.0
import "../../app/gui"

Item {
    id: window
    width: 800
    height: 650
    Item { id: toolBar }
    QtObject {
        id: streamSegueErrorDialog
        property string text: ""
        property bool quitAfter: false
        function open() {}
    }
    StackView { id: stackView; anchors.fill: parent }
    AppModel { id: testAppModel }
    Component { id: appPage; AppView { showGames: true; appModel: testAppModel } }
    TestCase {
        name: "AppTileLaunch"
        when: windowShown
        function init() {
            window.width = 800
            window.Window.window.width = 800
            window.Window.window.height = 650
            testAppModel.setProperty(0, "directLaunch", false)
            testAppModel.setProperty(0, "hidden", false)
            SessionTestState.initializations = 0
            SessionTestState.starts = 0
            toolBar.visible = true
            stackView.push(appPage, {}, StackView.Immediate)
            tryCompare(stackView.currentItem, "count", 1)
            wait(50)
        }
        function cleanup() {
            stackView.clear(StackView.Immediate)
            wait(20)
        }
        function test_menuHelpWrapsInsideNarrowMenuAndKeyboardStillWorks() {
            window.width = 320
            window.Window.window.width = 320
            var grid = stackView.currentItem
            var tile = null
            tryVerify(function() {
                for (var i = 0; i < grid.contentItem.children.length; i++) {
                    var child = grid.contentItem.children[i]
                    if (typeof child.launchOrResumeSelectedApp === "function") tile = child
                }
                return tile !== null && tile.appContextMenu !== null
            })
            var menu = tile.appContextMenu
            menu.popup(10, 10)
            tryCompare(menu, "visible", true)
            verify(menu.width <= window.width - 24)
            for (var index = 2; index <= 3; index++) {
                var option = menu.itemAt(index)
                var description = findChild(option, "menuDescription")
                verify(description !== null)
                verify(description.visible)
                verify(option.width <= menu.availableWidth + 1)
                verify(description.width <= menu.width)
                verify(description.paintedWidth <= description.width + 1)
                verify(description.lineCount > 1)
                verify(description.height >= description.implicitHeight - 1)
                verify(option.height >= option.contentItem.implicitHeight)
                verify(description.text.length > 40)
            }
            var direct = menu.itemAt(2)
            direct.forceActiveFocus()
            keyClick(Qt.Key_Return)
            tryCompare(menu, "visible", false)
            compare(testAppModel.get(0).directLaunch, true)
            compare(SessionTestState.starts, 0)
        }
        function test_componentFailureIsVisibleWithoutStartingSession() {
            var grid = stackView.currentItem
            compare(grid.loadLaunchComponent("MissingStreamPage.qml"), null)
            var dialog = findChild(grid, "launchPageError")
            verify(dialog !== null)
            tryCompare(dialog, "visible", true)
            verify(dialog.text.indexOf("could not be opened") >= 0)
            compare(SessionTestState.initializations, 0)
            compare(SessionTestState.starts, 0)
            dialog.close()
        }
        function test_streamPageLoads() {
            var page = Qt.createComponent("../../app/gui/StreamSegue.qml")
            compare(page.status, Component.Ready, page.errorString())
        }
        function test_clickStartsSelectedApp() {
            mouseClick(stackView.currentItem, 110, 140)
            tryCompare(SessionTestState, "initializations", 1)
            tryCompare(SessionTestState, "starts", 1)
            compare(stackView.depth, 2)
        }
    }
}
