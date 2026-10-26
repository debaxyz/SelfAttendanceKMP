package com.debaxyz.selfattendance.platform

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.firestore.FirebaseFirestore
import dev.gitlive.firebase.firestore.firestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import platform.Foundation.NSBundle
import platform.Foundation.NSNumber
import platform.Foundation.NSNumberFormatter
import platform.Foundation.NSNumberFormatterDecimalStyle
import platform.Foundation.NSString
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.numberWithLongLong
import platform.Foundation.stringWithContentsOfURL
import platform.Foundation.stringWithString
import platform.Foundation.writeToURL
import platform.Network.nw_path_get_status
import platform.Network.nw_path_monitor_create
import platform.Network.nw_path_monitor_set_queue
import platform.Network.nw_path_monitor_set_update_handler
import platform.Network.nw_path_monitor_start
import platform.Network.nw_path_status_satisfied
import platform.UIKit.UIApplication
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.UIViewController
import platform.UniformTypeIdentifiers.UTTypeJSON
import platform.UniformTypeIdentifiers.UTTypePlainText
import platform.darwin.NSObject
import platform.darwin.dispatch_get_main_queue

actual fun formatGrouped(n: Long): String {
    val f = NSNumberFormatter().apply { numberStyle = NSNumberFormatterDecimalStyle }
    return f.stringFromNumber(NSNumber.numberWithLongLong(n)) ?: n.toString()
}

@Composable
actual fun platformColorScheme(dark: Boolean): ColorScheme =
    if (dark) darkColorScheme() else lightColorScheme()

/** Status-bar icon colour follows the system on iOS (see MIGRATION.md, "Known gaps"). */
@Composable
actual fun SystemBarsEffect(dark: Boolean) {}

/** iOS has no system back button. */
@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {}

@Composable
actual fun rememberAppVersion(): String {
    val info = NSBundle.mainBundle.infoDictionary
    val v = info?.get("CFBundleShortVersionString") as? String ?: "1.0"
    val b = info?.get("CFBundleVersion") as? String
    return if (b != null) "$v ($b)" else v
}

/**
 * Default Firestore database only. The Android build can open a named database (R.string.firestore_database_id);
 * on iOS that needs the Firebase SDK's FIRFirestore.firestore(database:) via cinterop. See MIGRATION.md.
 */
actual fun createFirestore(): FirebaseFirestore = Firebase.firestore

class IosNetworkMonitor : NetworkMonitor {
    private val state = MutableStateFlow(true)

    init {
        val monitor = nw_path_monitor_create()
        nw_path_monitor_set_update_handler(monitor) { path ->
            state.value = path != null && nw_path_get_status(path) == nw_path_status_satisfied
        }
        nw_path_monitor_set_queue(monitor, dispatch_get_main_queue())
        nw_path_monitor_start(monitor)
    }

    override fun isOnline(): Boolean = state.value
    override fun observe(): Flow<Boolean> = state.distinctUntilChanged()
}

private fun topViewController(): UIViewController? {
    var vc = UIApplication.sharedApplication.keyWindow?.rootViewController
    while (vc?.presentedViewController != null) vc = vc.presentedViewController
    return vc
}

/** Keeps the picker delegate alive (UIDocumentPickerViewController.delegate is a weak reference). */
private class IosFileHolder {
    private var delegate: NSObject? = null

    fun export(name: String, content: String, msg: String, onToast: (String) -> Unit) {
        val url = NSURL.fileURLWithPath(NSTemporaryDirectory() + name)
        val ok = NSString.stringWithString(content).writeToURL(url, true, NSUTF8StringEncoding, null)
        if (!ok) { onToast("Could not save file."); return }
        val picker = UIDocumentPickerViewController(forExportingURLs = listOf(url), asCopy = true)
        val d = object : NSObject(), UIDocumentPickerDelegateProtocol {
            override fun documentPicker(controller: UIDocumentPickerViewController, didPickDocumentsAtURLs: List<*>) {
                delegate = null
                onToast(msg)
            }
            override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) { delegate = null }
        }
        delegate = d
        picker.delegate = d
        topViewController()?.presentViewController(picker, animated = true, completion = null)
    }

    fun pick(onToast: (String) -> Unit, onText: (String) -> Unit) {
        val types = listOfNotNull(UTTypeJSON, UTTypePlainText)
        val picker = UIDocumentPickerViewController(forOpeningContentTypes = types, asCopy = true)
        val d = object : NSObject(), UIDocumentPickerDelegateProtocol {
            override fun documentPicker(controller: UIDocumentPickerViewController, didPickDocumentsAtURLs: List<*>) {
                delegate = null
                val url = didPickDocumentsAtURLs.firstOrNull() as? NSURL
                val text = url?.let { NSString.stringWithContentsOfURL(it, NSUTF8StringEncoding, null) }
                if (text != null) onText(text) else onToast("Error reading backup file.")
            }
            override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) { delegate = null }
        }
        delegate = d
        picker.delegate = d
        topViewController()?.presentViewController(picker, animated = true, completion = null)
    }
}

@Composable
actual fun rememberFileActions(onToast: (String) -> Unit, onRestoreText: (String) -> Unit): FileActions {
    val toast by rememberUpdatedState(onToast)
    val restoreText by rememberUpdatedState(onRestoreText)
    val holder = remember { IosFileHolder() }
    return remember(holder) {
        FileActions(
            saveCsv = { (name, content, msg) -> holder.export(name, content, msg) { toast(it) } },
            saveJson = { (name, content, msg) -> holder.export(name, content, msg) { toast(it) } },
            restore = { holder.pick({ toast(it) }, { restoreText(it) }) }
        )
    }
}
