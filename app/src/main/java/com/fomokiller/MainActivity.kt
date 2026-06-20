package com.fomokiller

import android.Manifest
import android.content.ComponentName
import android.content.pm.ApplicationInfo
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.text.Html
import android.text.method.LinkMovementMethod
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.fomokiller.databinding.ActivityMainBinding
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.materialswitch.MaterialSwitch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var settingsBehavior: BottomSheetBehavior<View>
    private lateinit var gestureDetector: GestureDetector

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (!isGranted) {
            Toast.makeText(this, getString(R.string.toast_permission_denied), Toast.LENGTH_LONG).show()
            findViewById<MaterialSwitch>(R.id.switchRedisplay)?.isChecked = false
            AppState.reDisplayNotifications = false
        }
    }

    private val serviceStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            updateUI()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppState.init(applicationContext)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupSettingsPanel()
        setupGestureDetector()

        AppState.openCount++
        if (AppState.openCount % 6 == 0 && !isIgnoringBatteryOptimizations()) {
            showBatteryTipSheet()
        }

        setupButtons()
        updateUI()
    }

    private fun setupGestureDetector() {
        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                if (e1 != null && e2 != null) {
                    val diffY = e2.y - e1.y
                    if (diffY < -100 && Math.abs(velocityY) > 100) { // Swipe UP
                        settingsBehavior.state = BottomSheetBehavior.STATE_EXPANDED
                        return true
                    }
                }
                return false
            }
        })
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (settingsBehavior.state == BottomSheetBehavior.STATE_HIDDEN || settingsBehavior.state == BottomSheetBehavior.STATE_SETTLING) {
            gestureDetector.onTouchEvent(ev)
        }
        return super.dispatchTouchEvent(ev)
    }

    private fun setupSettingsPanel() {
        val bottomSheet = findViewById<View>(R.id.settingsBottomSheet)
        val scrim = findViewById<View>(R.id.settingsScrim)
        settingsBehavior = BottomSheetBehavior.from(bottomSheet)
        settingsBehavior.state = BottomSheetBehavior.STATE_HIDDEN

        settingsBehavior.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(bottomSheet: View, newState: Int) {
                if (newState == BottomSheetBehavior.STATE_HIDDEN) {
                    scrim.visibility = View.GONE
                } else {
                    scrim.visibility = View.VISIBLE
                }
            }

            override fun onSlide(bottomSheet: View, slideOffset: Float) {
                val alpha = slideOffset.coerceIn(0f, 1f)
                scrim.alpha = alpha * 0.6f
                scrim.visibility = if (alpha > 0) View.VISIBLE else View.GONE
            }
        })

        scrim.setOnClickListener {
            settingsBehavior.state = BottomSheetBehavior.STATE_HIDDEN
        }

        val switchRedisplay = findViewById<MaterialSwitch>(R.id.switchRedisplay)
        switchRedisplay.isChecked = AppState.reDisplayNotifications
        switchRedisplay.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
            AppState.reDisplayNotifications = isChecked
        }

        val prefs = getSharedPreferences("fomokiller_prefs", Context.MODE_PRIVATE)
        val toggleGroup = findViewById<com.google.android.material.button.MaterialButtonToggleGroup>(R.id.toggleGroupTileMode)
        val initialMode = prefs.getString("tile_target_mode", "KILL_ALL") ?: "KILL_ALL"

        if (initialMode == "KILL_ALL") {
            toggleGroup.check(R.id.btnTileKillAll)
        } else {
            toggleGroup.check(R.id.btnTileVipOnly)
        }

        toggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                val modeStr = if (checkedId == R.id.btnTileKillAll) "KILL_ALL" else "VIP_ONLY"
                prefs.edit().putString("tile_target_mode", modeStr).apply()
            }
        }

        loadAboutMarkdown()
    }

    private fun loadAboutMarkdown() {
        val textAbout = findViewById<TextView>(R.id.textAbout)
        try {
            val content = assets.open("about.md").bufferedReader().use { it.readText() }
            val htmlContent = content
                .replace(Regex("\\[(.*?)\\]\\((.*?)\\)"), "<a href=\"$2\">$1</a>")
                .replace("\n", "<br/>")

            textAbout.text = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                Html.fromHtml(htmlContent, Html.FROM_HTML_MODE_COMPACT)
            } else {
                @Suppress("DEPRECATION")
                Html.fromHtml(htmlContent)
            }
            textAbout.movementMethod = LinkMovementMethod.getInstance()
        } catch (e: Exception) {
            textAbout.text = "FomoKiller v1.1"
        }
    }

    private fun isIgnoringBatteryOptimizations(): Boolean {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(packageName)
    }

    private fun showBatteryTipSheet() {
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.bottom_sheet_battery_tip, null)
        dialog.setContentView(view)

        view.findViewById<View>(R.id.btnOpenBatterySettings).setOnClickListener {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:$packageName")
            }
            try {
                startActivity(intent)
            } catch (e: Exception) {
                val fallbackIntent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                startActivity(fallbackIntent)
            }
            dialog.dismiss()
        }

        view.findViewById<View>(R.id.btnIgnoreBattery).setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    override fun onResume() {
        super.onResume()
        val filter = IntentFilter(FomoNotificationService.ACTION_STATE_CHANGED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(serviceStateReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(serviceStateReceiver, filter)
        }
        updateUI()
    }

    override fun onPause() {
        super.onPause()
        try { unregisterReceiver(serviceStateReceiver) } catch (_: Exception) {}
    }

    private fun setupButtons() {
        binding.btnOff.setOnClickListener {
            updateMode(FomoMode.OFF)
        }
        binding.btnKillAll.setOnClickListener {
            updateMode(FomoMode.KILL_ALL)
        }
        binding.btnVipOnly.setOnClickListener {
            updateMode(FomoMode.VIP_ONLY)
        }
        binding.btnKillAll.setOnLongClickListener {
            showAppPickerSheet(mode = "blocked")
            true
        }
        binding.btnVipOnly.setOnLongClickListener {
            showAppPickerSheet(mode = "vip")
            true
        }
    }

    private fun updateMode(mode: FomoMode) {
        if (!isNotificationListenerEnabled()) {
            requestNotificationAccess()
            return
        }
        AppState.currentMode = mode

        val service = FomoNotificationService.instance
        if (service != null) {
            service.applyCurrentMode()
        } else {
            val componentName = ComponentName(this, FomoNotificationService::class.java)
            NotificationListenerService.requestRebind(componentName)
            Toast.makeText(this, getString(R.string.toast_service_init), Toast.LENGTH_SHORT).show()
        }
        updateUI()
    }

    private fun showAppPickerSheet(mode: String) {
        val dialog = BottomSheetDialog(this)
        val sheetView = layoutInflater.inflate(R.layout.bottom_sheet_app_picker, null)
        dialog.setContentView(sheetView)

        val title = sheetView.findViewById<TextView>(R.id.sheetTitle)
        val subtitle = sheetView.findViewById<TextView>(R.id.sheetSubtitle)
        val loading = sheetView.findViewById<ProgressBar>(R.id.sheetLoading)
        val recycler = sheetView.findViewById<RecyclerView>(R.id.sheetRecyclerView)
        val btnCancel = sheetView.findViewById<com.google.android.material.button.MaterialButton>(R.id.sheetBtnCancel)
        val btnConfirm = sheetView.findViewById<com.google.android.material.button.MaterialButton>(R.id.sheetBtnConfirm)

        title.text = if (mode == "blocked") getString(R.string.picker_title_blocked) else getString(R.string.picker_title_vip)
        subtitle.text = if (mode == "blocked") getString(R.string.picker_subtitle_blocked) else getString(R.string.picker_subtitle_vip)

        val selectedApps = mutableSetOf<String>().apply {
            addAll(if (mode == "blocked") AppState.blockedApps else AppState.vipApps)
        }

        val allApps = mutableListOf<AppInfo>()
        val adapter = AppSheetAdapter(allApps, selectedApps) { count ->
            btnConfirm.text = if (count > 0) getString(R.string.picker_confirm_count, count) else getString(R.string.picker_confirm)
        }
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter

        Thread {
            try {
                val pm = packageManager
                val installed = pm.getInstalledPackages(PackageManager.GET_META_DATA)
                val apps = installed
                    .filter { pkg ->
                        val isUser = (pkg.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) == 0
                        val hasLauncher = pm.getLaunchIntentForPackage(pkg.packageName) != null
                        (isUser || hasLauncher) && pkg.packageName != packageName
                    }
                    .map { pkg ->
                        AppInfo(
                            packageName = pkg.packageName,
                            label = pm.getApplicationLabel(pkg.applicationInfo).toString(),
                            icon = try { pm.getApplicationIcon(pkg.packageName) } catch (_: Exception) { null }
                        )
                    }
                    .sortedBy { it.label.lowercase() }

                runOnUiThread {
                    allApps.clear()
                    allApps.addAll(apps)
                    adapter.notifyDataSetChanged()
                    loading.visibility = View.GONE
                    recycler.visibility = View.VISIBLE
                    val count = selectedApps.size
                    btnConfirm.text = if (count > 0) getString(R.string.picker_confirm_count, count) else getString(R.string.picker_confirm)
                }
            } catch (e: Exception) {
                runOnUiThread {
                    loading.visibility = View.GONE
                    Toast.makeText(this, getString(R.string.toast_error, e.message), Toast.LENGTH_LONG).show()
                }
            }
        }.start()

        btnCancel.setOnClickListener { dialog.dismiss() }
        btnConfirm.setOnClickListener {
            if (mode == "blocked") AppState.blockedApps = selectedApps.toSet()
            else AppState.vipApps = selectedApps.toSet()

            val service = FomoNotificationService.instance
            if (service != null) {
                service.applyCurrentMode()
            } else {
                val componentName = ComponentName(this, FomoNotificationService::class.java)
                NotificationListenerService.requestRebind(componentName)
            }

            updateUI()
            dialog.dismiss()
        }

        dialog.show()
    }

    data class AppInfo(
        val packageName: String,
        val label: String,
        val icon: android.graphics.drawable.Drawable?
    )

    inner class AppSheetAdapter(
        private val apps: List<AppInfo>,
        private val selected: MutableSet<String>,
        private val onSelectionChanged: (Int) -> Unit
    ) : RecyclerView.Adapter<AppSheetAdapter.VH>() {

        inner class VH(view: View) : RecyclerView.ViewHolder(view) {
            val icon: ImageView = view.findViewById(R.id.appIcon)
            val label: TextView = view.findViewById(R.id.appLabel)
            val pkg: TextView = view.findViewById(R.id.appPackage)
            val checkbox: CheckBox = view.findViewById(R.id.appCheckbox)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_app, parent, false)
            return VH(v)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val app = apps[position]
            holder.label.text = app.label
            holder.pkg.text = app.packageName
            holder.icon.setImageDrawable(app.icon)
            holder.checkbox.isChecked = selected.contains(app.packageName)

            val toggle = View.OnClickListener {
                if (selected.contains(app.packageName)) selected.remove(app.packageName)
                else selected.add(app.packageName)
                holder.checkbox.isChecked = selected.contains(app.packageName)
                onSelectionChanged(selected.size)
            }
            holder.itemView.setOnClickListener(toggle)
            holder.checkbox.setOnClickListener(toggle)
        }

        override fun getItemCount() = apps.size
    }

    private fun updateUI() {
        val listenerEnabled = isNotificationListenerEnabled()
        val mode = AppState.currentMode
        binding.permissionBanner.visibility = if (!listenerEnabled) View.VISIBLE else View.GONE
        binding.btnOff.isSelected = (mode == FomoMode.OFF)
        binding.btnKillAll.isSelected = (mode == FomoMode.KILL_ALL)
        binding.btnVipOnly.isSelected = (mode == FomoMode.VIP_ONLY)

        val blockedCount = AppState.blockedApps.size
        val vipCount = AppState.vipApps.size

        binding.labelKillAll.text = if (blockedCount > 0)
            getString(R.string.label_blocked_count, blockedCount)
        else
            getString(R.string.mode_kill_all_desc)

        binding.labelVipOnly.text = if (vipCount > 0)
            getString(R.string.label_vip_count, vipCount)
        else
            getString(R.string.mode_vip_only_desc)
    }

    private fun isNotificationListenerEnabled(): Boolean {
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        return flat != null && flat.contains(packageName)
    }

    private fun requestNotificationAccess() {
        Toast.makeText(this, getString(R.string.toast_grant_notification_access), Toast.LENGTH_LONG).show()
        startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
    }
}