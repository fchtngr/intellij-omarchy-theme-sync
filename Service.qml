import QtQuick
import Quickshell.Io

Item {
  id: root

  property var manifest: null

  function install() {
    if (!root.manifest || installer.running) return
    installer.command = ["bash", root.manifest.__sourceDir + "/install.sh"]
    installer.running = true
  }

  Process {
    id: installer
  }

  onManifestChanged: Qt.callLater(root.install)
}
