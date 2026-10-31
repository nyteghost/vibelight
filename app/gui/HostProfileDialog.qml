import QtQuick 2.9
import QtQuick.Controls 2.2
import QtQuick.Layouts 1.3

NavigableDialog {
    id: profileDialog
    property var profileModel
    property string hostId: ""
    property string hostName: ""
    property string monitorChoice: ""
    property string problem: ""
    title: qsTr("Streaming profile")
    width: Math.min(480, Math.max(1, Overlay.overlay.width - 32))
    closePolicy: Popup.CloseOnEscape

    function edit(profile) {
        if (!profile.uuid) return
        hostId = profile.uuid
        hostName = profile.name
        monitorChoice = profile.monitor || qsTr("Host default")
        custom.checked = profile.enabled
        videoWidth.value = profile.width
        videoHeight.value = profile.height
        frameRate.value = profile.fps
        bitrate.value = profile.bitrateKbps
        adaptive.checked = profile.adaptiveBitrate
        problem = profile.invalid ? qsTr("The saved profile is invalid or from an unsupported version. Global defaults will be used until you explicitly save a replacement.") : ""
        open()
        custom.forceActiveFocus()
    }

    function editedValue(control) {
        var number = control.valueFromText(control.contentItem.text, control.locale)
        return isFinite(number) && Math.floor(number) === number && number >= control.from && number <= control.to ? number : -1
    }

    function saveProfile() {
        // Read editor text too: clicking Save must not silently use an older value.
        var values = { width: editedValue(videoWidth), height: editedValue(videoHeight),
            fps: editedValue(frameRate), bitrateKbps: editedValue(bitrate), adaptiveBitrate: adaptive.checked }
        if (custom.checked && (values.width < 0 || values.height < 0 || values.fps < 0 || values.bitrateKbps < 0)) {
            problem = qsTr("Enter valid numbers within the displayed ranges before saving.")
            return
        }
        problem = profileModel.saveProfileForComputer(hostId, values, custom.checked)
        if (!problem) accept()
    }

    contentItem: ScrollView {
        id: scroll
        clip: true
        implicitHeight: Math.min(form.implicitHeight, Math.max(160, Overlay.overlay.height - 190))
        contentWidth: availableWidth
        ColumnLayout {
            id: form
            width: scroll.availableWidth
            spacing: 12
            Label { text: profileDialog.hostName; textFormat: Text.PlainText; font.bold: true; wrapMode: Text.Wrap; Layout.fillWidth: true }
            CheckBox { id: custom; text: qsTr("Use a custom profile for this PC"); Layout.fillWidth: true }
            Label {
                text: qsTr("Save applies to the next new stream launched from the app. Existing streams and reconnects keep their current settings. Other PCs and global defaults are unchanged.")
                wrapMode: Text.Wrap; Layout.fillWidth: true
            }
            GridLayout {
                columns: scroll.availableWidth >= 360 ? 2 : 1; enabled: custom.checked; Layout.fillWidth: true
                Label { text: qsTr("Width (pixels)") }
                SpinBox { id: videoWidth; from: 256; to: 8192; stepSize: 2; editable: true; Layout.fillWidth: true; Accessible.name: qsTr("Profile width") }
                Label { text: qsTr("Height (pixels)") }
                SpinBox { id: videoHeight; from: 256; to: 8192; stepSize: 2; editable: true; Layout.fillWidth: true; Accessible.name: qsTr("Profile height") }
                Label { text: qsTr("Frame rate (FPS)") }
                SpinBox { id: frameRate; from: 10; to: 9999; editable: true; Layout.fillWidth: true; Accessible.name: qsTr("Profile frame rate") }
                Label { text: qsTr("Bitrate ceiling (kbps)") }
                SpinBox { id: bitrate; from: 500; to: 150000; stepSize: 500; editable: true; Layout.fillWidth: true; Accessible.name: qsTr("Profile bitrate ceiling") }
            }
            CheckBox { id: adaptive; enabled: custom.checked; text: qsTr("Adapt bitrate during streaming"); Layout.fillWidth: true }
            Label { text: qsTr("Adaptive bitrate requires a compatible Vibebeam host. Codec, HDR, audio and input controls inherit global settings."); wrapMode: Text.Wrap; Layout.fillWidth: true }
            Label { text: qsTr("Saved monitor: %1").arg(profileDialog.monitorChoice); textFormat: Text.PlainText; wrapMode: Text.Wrap; Layout.fillWidth: true }
            Label { text: qsTr("Use Choose Monitor in the PC menu to change its saved monitor. Disabling this quality profile preserves that choice."); wrapMode: Text.Wrap; Layout.fillWidth: true }
            Label { text: profileDialog.problem; visible: text.length > 0; wrapMode: Text.Wrap; Layout.fillWidth: true; color: "#FBBF24"; Accessible.role: Accessible.AlertMessage }
        }
    }
    footer: DialogButtonBox {
        Button { text: qsTr("Save"); DialogButtonBox.buttonRole: DialogButtonBox.ActionRole; onClicked: profileDialog.saveProfile() }
        Button { text: qsTr("Cancel"); DialogButtonBox.buttonRole: DialogButtonBox.RejectRole }
        onRejected: profileDialog.reject()
    }
}
