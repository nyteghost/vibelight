import QtQuick 2.9
import QtQuick.Controls 2.2
import QtQuick.Layouts 1.3

import ComputerModel 1.0

import ComputerManager 1.0
import StreamingPreferences 1.0
import SystemProperties 1.0
import SdlGamepadKeyNavigation 1.0

CenteredGridView {
    property ComputerModel computerModel : createModel()

    id: pcGrid
    focus: true
    activeFocusOnTab: true
    topMargin: 20
    bottomMargin: 5
    // Fit one complete card on narrower windows while retaining the familiar
    // 350px tile and keyboard/gamepad GridView navigation on larger screens.
    cellWidth: Math.min(350, Math.max(260, width - 20))
    cellHeight: 344
    objectName: qsTr("Computers")

    Component.onCompleted: {
        // Don't show any highlighted item until interacting with them.
        // We do this here instead of onActivated to avoid losing the user's
        // selection when backing out of a different page of the app.
        currentIndex = -1
    }

    // Note: Any initialization done here that is critical for streaming must
    // also be done in CliStartStreamSegue.qml, since this code does not run
    // for command-line initiated streams.
    StackView.onActivated: {
        // Setup signals on CM
        ComputerManager.computerAddCompleted.connect(addComplete)

        // Highlight the first item if a gamepad is connected
        if (currentIndex === -1 && SdlGamepadKeyNavigation.getConnectedGamepads() > 0) {
            currentIndex = 0
        }
    }

    StackView.onDeactivating: {
        ComputerManager.computerAddCompleted.disconnect(addComplete)
    }

    function pairingComplete(error)
    {
        // Close the PIN dialog
        pairDialog.close()

        // Display a failed dialog if we got an error
        if (error !== undefined) {
            errorDialog.text = error
            errorDialog.helpText = ""
            errorDialog.open()
        }
    }

    function addComplete(success, detectedPortBlocking)
    {
        if (!success) {
            errorDialog.text = qsTr("Unable to connect to the specified PC.")

            if (detectedPortBlocking) {
                errorDialog.text += "\n\n" + qsTr("This PC's Internet connection is blocking Vibelight. Streaming over the Internet may not work while connected to this network.")
            }
            else {
                errorDialog.helpText = qsTr("Click the Help button for possible solutions.")
            }

            errorDialog.open()
        }
    }

    function createModel()
    {
        var model = Qt.createQmlObject('import ComputerModel 1.0; ComputerModel {}', parent, '')
        model.initialize(ComputerManager)
        model.pairingCompleted.connect(pairingComplete)
        model.connectionTestCompleted.connect(testConnectionDialog.connectionTestComplete)
        model.monitorsReady.connect(chooseMonitorDialog.monitorsReady)
        return model
    }

    // Home dashboard: the header scrolls with the cards, keeping controller
    // and keyboard GridView navigation unchanged.
    header: Item {
        width: pcGrid.width
        height: pcGrid.count > 0 ? (pcGrid.width < 600 ? 126 : 108) : 0
        visible: pcGrid.count > 0

        Column {
            anchors.left: parent.left
            anchors.leftMargin: 24
            anchors.right: parent.right
            anchors.top: parent.top
            anchors.topMargin: 22
            spacing: 8

            Label {
                text: qsTr("YOUR COMPUTERS")
                font.pixelSize: 12
                font.bold: true
                font.letterSpacing: 2
                color: "#818CF8"
            }
            Label {
                text: qsTr("Choose a host to start streaming")
                font.pixelSize: 24
                font.weight: Font.DemiBold
                color: "#E2E8F0"
                elide: Text.ElideRight
                width: parent.width
            }
            Label {
                text: qsTr("Select a host to open its apps. Display chooses the monitor for your next stream.")
                font.pixelSize: 13
                color: "#94A3B8"
                wrapMode: Text.WordWrap
                width: parent.width
            }
        }
    }

    Row {
        anchors.centerIn: parent
        spacing: 5
        visible: pcGrid.count === 0

        BusyIndicator {
            id: searchSpinner
            visible: StreamingPreferences.enableMdns
            running: visible
        }

        Label {
            height: searchSpinner.height
            elide: Label.ElideRight
            text: StreamingPreferences.enableMdns ? qsTr("Searching for compatible hosts on your local network...")
                                                  : qsTr("Automatic PC discovery is disabled. Add your PC manually.")
            font.pointSize: 20
            verticalAlignment: Text.AlignVCenter
            wrapMode: Text.Wrap
        }
    }

    model: computerModel

    delegate: NavigableItemDelegate {
        width: pcGrid.cellWidth - 12
        height: pcGrid.cellHeight - 12
        grid: pcGrid

        property alias pcContextMenu : pcContextMenuLoader.item

        Rectangle {
            id: availabilityBadge
            anchors.top: parent.top
            anchors.topMargin: 20
            anchors.right: parent.right
            anchors.rightMargin: 20
            width: availabilityText.implicitWidth + 24
            height: 28
            radius: 14
            color: model.statusUnknown ? "#1E293B" : (model.online ? "#123C35" : "#38232B")
            border.color: model.statusUnknown ? "#475569" : (model.online ? "#166954" : "#6F3642")

            Label {
                id: availabilityText
                anchors.centerIn: parent
                text: model.statusUnknown ? qsTr("Checking") : (model.online ? qsTr("Online") : qsTr("Offline"))
                color: model.statusUnknown ? "#CBD5E1" : (model.online ? "#86EFAC" : "#FDA4AF")
                font.pixelSize: 12
                font.bold: true
            }
        }

        Image {
            id: pcIcon
            anchors.horizontalCenter: parent.horizontalCenter
            y: 65
            source: "qrc:/res/desktop_windows-48px.svg"
            sourceSize {
                width: 110
                height: 110
            }
        }

        Image {
            // TODO: Tooltip
            id: stateIcon
            anchors.horizontalCenter: pcIcon.horizontalCenter
            anchors.verticalCenter: pcIcon.verticalCenter
            anchors.verticalCenterOffset: !model.online ? -18 : -16
            visible: !model.statusUnknown && (!model.online || !model.paired)
            source: !model.online ? "qrc:/res/warning_FILL1_wght300_GRAD200_opsz24.svg" : "qrc:/res/baseline-lock-24px.svg"
            sourceSize {
                width: !model.online ? 75 : 70
                height: !model.online ? 75 : 70
            }
        }

        BusyIndicator {
            id: statusUnknownSpinner
            anchors.horizontalCenter: pcIcon.horizontalCenter
            anchors.verticalCenter: pcIcon.verticalCenter
            anchors.verticalCenterOffset: -15
            width: 75
            height: 75
            visible: model.statusUnknown
            running: visible
        }

        Label {
            id: pcNameText
            text: model.name

            width: parent.width - 40
            anchors.horizontalCenter: parent.horizontalCenter
            anchors.top: pcIcon.bottom
            anchors.topMargin: 12
            height: 48
            font.pixelSize: 25
            horizontalAlignment: Text.AlignHCenter
            wrapMode: Text.Wrap
            elide: Text.ElideRight
        }

        Label {
            id: hostStatus
            anchors.top: pcNameText.bottom
            anchors.topMargin: 2
            anchors.horizontalCenter: parent.horizontalCenter
            width: parent.width - 32
            text: model.statusUnknown ? qsTr("Checking host availability…") :
                  !model.online ? (model.wakeable ? qsTr("Offline — Wake PC is available") : qsTr("Host offline — check connection")) :
                  !model.paired ? qsTr("Pair this computer to continue") :
                                  qsTr("Ready · %1").arg(model.profileSummary)
            color: "#94A3B8"
            font.pixelSize: 13
            horizontalAlignment: Text.AlignHCenter
            elide: Text.ElideRight
        }

        Label {
            anchors.left: parent.left
            anchors.leftMargin: 24
            anchors.bottom: parent.bottom
            anchors.bottomMargin: 21
            text: model.statusUnknown ? qsTr("Checking…") :
                  model.online ? qsTr("Select to connect") : qsTr("Open for options")
            color: "#818CF8"
            font.pixelSize: 13
            font.bold: true
        }

        Button {
            anchors.right: parent.right
            anchors.rightMargin: 20
            anchors.bottom: parent.bottom
            anchors.bottomMargin: 12
            visible: model.online && model.paired
            focusPolicy: Qt.NoFocus // Card remains the keyboard/gamepad focus target
            text: qsTr("Display…")
            font.pixelSize: 12
            onClicked: computerModel.chooseMonitorForComputer(index)
            ToolTip.visible: hovered
            ToolTip.text: qsTr("Choose a physical or virtual monitor for this host (D key)")
        }

        Loader {
            id: pcContextMenuLoader
            asynchronous: true
            sourceComponent: NavigableMenu {
                id: pcContextMenu
                initiator: pcContextMenuLoader.parent
                MenuItem {
                    text: qsTr("PC Status: %1").arg(model.online ? qsTr("Online") : qsTr("Offline"))
                    font.bold: true
                    enabled: false
                }
                NavigableMenuItem {
                    text: qsTr("View All Apps")
                    onTriggered: {
                        var component = Qt.createComponent("AppView.qml")
                        var appView = component.createObject(stackView, {"computerIndex": index, "objectName": model.name, "showHiddenGames": true})
                        stackView.push(appView)
                    }
                    visible: model.online && model.paired
                }
                NavigableMenuItem {
                    text: qsTr("Wake PC")
                    onTriggered: computerModel.wakeComputer(index)
                    visible: !model.online && model.wakeable
                }
                NavigableMenuItem {
                    text: qsTr("Test Network")
                    onTriggered: {
                        computerModel.testConnectionForComputer(index)
                        testConnectionDialog.open()
                    }
                }

                NavigableMenuItem {
                    text: qsTr("Streaming profile…")
                    onTriggered: hostProfileDialog.edit(computerModel.profileForComputer(index))
                }

                NavigableMenuItem {
                    text: qsTr("Choose Monitor...")
                    onTriggered: computerModel.chooseMonitorForComputer(index)
                    visible: model.online && model.paired
                }

                NavigableMenuItem {
                    text: qsTr("Rename PC")
                    onTriggered: {
                        renamePcDialog.pcIndex = index
                        renamePcDialog.originalName = model.name
                        renamePcDialog.open()
                    }
                }
                NavigableMenuItem {
                    text: qsTr("Delete PC")
                    onTriggered: {
                        deletePcDialog.pcIndex = index
                        deletePcDialog.pcName = model.name
                        deletePcDialog.open()
                    }
                }
                NavigableMenuItem {
                    text: qsTr("View Details")
                    onTriggered: {
                        showPcDetailsDialog.pcDetails = model.details
                        showPcDetailsDialog.open()
                    }
                }
            }
        }

        onClicked: {
            if (model.online) {
                if (!model.serverSupported) {
                    errorDialog.text = qsTr("The version of GeForce Experience on %1 is not supported by this build of Vibelight. You must update Vibelight to stream from %1.").arg(model.name)
                    errorDialog.helpText = ""
                    errorDialog.open()
                }
                else if (model.paired) {
                    // go to game view
                    var component = Qt.createComponent("AppView.qml")
                    var appView = component.createObject(stackView, {"computerIndex": index, "objectName": model.name})
                    stackView.push(appView)
                }
                else {
                    var pin = computerModel.generatePinString()

                    // Kick off pairing in the background
                    computerModel.pairComputer(index, pin)

                    // Display the pairing dialog
                    pairDialog.pin = pin
                    pairDialog.open()
                }
            } else if (!model.online) {
                // Using open() here because it may be activated by keyboard
                pcContextMenu.open()
            }
        }

        onPressAndHold: {
            // popup() ensures the menu appears under the mouse cursor
            if (pcContextMenu.popup) {
                pcContextMenu.popup()
            }
            else {
                // Qt 5.9 doesn't have popup()
                pcContextMenu.open()
            }
        }

        MouseArea {
            anchors.fill: parent
            acceptedButtons: Qt.RightButton;
            onClicked: {
                parent.pressAndHold()
            }
        }

        // Keyboard alternative to the Display button; controller users can
        // use the existing context menu to reach the same action.
        Keys.onPressed: {
            if (event.key === Qt.Key_D && model.online && model.paired) {
                computerModel.chooseMonitorForComputer(index)
                event.accepted = true
            }
        }

        Keys.onMenuPressed: {
            // We must use open() here so the menu is positioned on
            // the ItemDelegate and not where the mouse cursor is
            pcContextMenu.open()
        }

        Keys.onDeletePressed: {
            deletePcDialog.pcIndex = index
            deletePcDialog.pcName = model.name
            deletePcDialog.open()
        }
    }

    ErrorMessageDialog {
        id: errorDialog

        // Using Setup-Guide here instead of Troubleshooting because it's likely that users
        // will arrive here by forgetting to enable GameStream or not forwarding ports.
        helpUrl: "https://github.com/moonlight-stream/moonlight-docs/wiki/Setup-Guide"
    }

    NavigableMessageDialog {
        id: pairDialog
        closePolicy: Popup.CloseOnEscape

        // don't allow edits to the rest of the window while open
        property string pin : "0000"
        text:qsTr("Please enter %1 on your host PC. This dialog will close when pairing is completed.").arg(pin)+"\n\n"+
             qsTr("If your host PC is running Sunshine, navigate to the Sunshine web UI to enter the PIN.")
        standardButtons: Dialog.Cancel
        onRejected: {
            // FIXME: We should interrupt pairing here
        }
    }

    NavigableMessageDialog {
        id: deletePcDialog
        // don't allow edits to the rest of the window while open
        property int pcIndex : -1
        property string pcName : ""
        text: qsTr("Are you sure you want to remove '%1'?").arg(pcName)
        standardButtons: Dialog.Yes | Dialog.No

        onAccepted: {
            computerModel.deleteComputer(pcIndex)
        }
    }

    NavigableMessageDialog {
        id: testConnectionDialog
        closePolicy: Popup.CloseOnEscape
        standardButtons: Dialog.Ok

        onAboutToShow: {
            testConnectionDialog.text = qsTr("Vibelight is testing your network connection to determine if any required ports are blocked.") + "\n\n" + qsTr("This may take a few seconds…")
            showSpinner = true
        }

        function connectionTestComplete(result, blockedPorts)
        {
            if (result === -1) {
                text = qsTr("The network test could not be performed because none of Vibelight's connection testing servers were reachable from this PC. Check your Internet connection or try again later.")
                imageSrc = "qrc:/res/baseline-warning-24px.svg"
            }
            else if (result === 0) {
                text = qsTr("This network does not appear to be blocking Vibelight. If you still have trouble connecting, check your PC's firewall settings.") + "\n\n" + qsTr("If you are trying to stream over the Internet, install the Moonlight Internet Hosting Tool on your gaming PC and run the included Internet Streaming Tester to check your gaming PC's Internet connection.")
                imageSrc = "qrc:/res/baseline-check_circle_outline-24px.svg"
            }
            else {
                text = qsTr("Your PC's current network connection seems to be blocking Vibelight. Streaming over the Internet may not work while connected to this network.") + "\n\n" + qsTr("The following network ports were blocked:") + "\n"
                text += blockedPorts
                imageSrc = "qrc:/res/baseline-error_outline-24px.svg"
            }

            // Stop showing the spinner and show the image instead
            showSpinner = false
        }
    }

    HostProfileDialog {
        id: hostProfileDialog
        profileModel: computerModel
    }

    NavigableDialog {
        id: renamePcDialog
        property string label: qsTr("Enter the new name for this PC:")
        property string originalName
        property int pcIndex : -1;

        standardButtons: Dialog.Ok | Dialog.Cancel

        onOpened: {
            // Force keyboard focus on the textbox so keyboard navigation works
            editText.forceActiveFocus()
        }

        onClosed: {
            editText.clear()
        }

        onAccepted: {
            if (editText.text) {
                computerModel.renameComputer(pcIndex, editText.text)
            }
        }

        ColumnLayout {
            Label {
                text: renamePcDialog.label
                font.bold: true
            }

            TextField {
                id: editText
                placeholderText: renamePcDialog.originalName
                Layout.fillWidth: true
                focus: true

                Keys.onReturnPressed: {
                    renamePcDialog.accept()
                }

                Keys.onEnterPressed: {
                    renamePcDialog.accept()
                }
            }
        }
    }

    NavigableDialog {
        id: chooseMonitorDialog
        property int pcIndex: -1
        property string problem: ""
        property var monitorIds: []

        function monitorsReady(computerIndex, ids, names, current, error) {
            pcIndex = computerIndex
            problem = error
            monitorIds = ["", "@virtual"].concat(ids)
            monitorCombo.model = [qsTr("Let the PC decide"), qsTr("Virtual display")].concat(names)
            var at = monitorIds.indexOf(current)
            monitorCombo.currentIndex = at >= 0 ? at : 0
            open()
        }

        standardButtons: problem ? Dialog.Ok : (Dialog.Ok | Dialog.Cancel)

        onAccepted: {
            if (!problem && pcIndex >= 0) {
                computerModel.setMonitorForComputer(pcIndex, monitorIds[monitorCombo.currentIndex])
            }
        }

        ColumnLayout {
            Label {
                text: chooseMonitorDialog.problem ? chooseMonitorDialog.problem
                                                  : qsTr("Which monitor should this PC stream? \"Virtual display\" asks the host for a separate virtual screen. Applies the next time you start a stream.")
                font.bold: true
                wrapMode: Text.Wrap
                Layout.maximumWidth: 420
            }

            ComboBox {
                id: monitorCombo
                visible: !chooseMonitorDialog.problem
                Layout.fillWidth: true
                focus: true
            }
        }
    }

    NavigableMessageDialog {
        id: showPcDetailsDialog
        property string pcDetails : "";
        text: showPcDetailsDialog.pcDetails
        imageSrc: "qrc:/res/baseline-help_outline-24px.svg"
        standardButtons: Dialog.Ok
    }

    ScrollBar.vertical: ScrollBar {}
}
