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
import android.view.LayoutInflater
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

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (!isGranted) {
            Toast.makeText(this, "Permission refusée : les notifications ne pourront pas être restaurées", Toast.LENGTH_LONG).show()
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
        
        AppState.openCount++
        if (AppState.openCount % 6 == 0 && !isIgnoringBatteryOptimizations()) {
            showBatteryTipSheet()
        }
        
        setupButtons()
        updateUI()
    }

    private fun setupSettingsPanel() {
        val bottomSheet = findViewById<View>(R.id.settingsBottomSheet)
        settingsBehavior = BottomSheetBehavior.from(bottomSheet)
        
        val header = findViewById<View>(R.id.settingsHeader)
        header.setOnClickListener {
            if (settingsBehavior.state == BottomSheetBehavior.STATE_COLLAPSED) {
                settingsBehavior.state = BottomSheetBehavior.STATE_EXPANDED
            } else {
                settingsBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
            }
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
            // Si le service n'est pas lié, on demande au système de le relancer
            val componentName = ComponentName(this, FomoNotificationService::class.java)
            NotificationListenerService.requestRebind(componentName)
            Toast.makeText(this, "Initialisation du service...", Toast.LENGTH_SHORT).show()
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

        title.text = if (mode == "blocked") "Apps à bloquer" else "Apps VIP"
        subtitle.text = if (mode == "blocked") "Bloquées en mode Activé" else "Autorisées en mode Protégé"

        val selectedApps = mutableSetOf<String>().apply {
            addAll(if (mode == "blocked") AppState.blockedApps else AppState.vipApps)
        }

        val allApps = mutableListOf<AppInfo>()
        val adapter = AppSheetAdapter(allApps, selectedApps) { count ->
            btnConfirm.text = if (count > 0) "Confirmer ($count)" else "Confirmer"
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
                    btnConfirm.text = if (count > 0) "Confirmer ($count)" else "Confirmer"
                }
            } catch (e: Exception) {
                runOnUiThread {
                    loading.visibility = View.GONE
                    Toast.makeText(this, "Erreur: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }.start()

        btnCancel.setOnClickListener { dialog.dismiss() }
        btnConfirm.setOnClickListener {
            if (mode == "blocked") AppState.blockedApps = selectedApps.toSet()
            else AppState.vipApps = selectedApps.toSet()
            
            // Réappliquer immédiatement
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
        
        // Mode ACTIVÉ (KILL_ALL) : Bloque uniquement la liste noire
        binding.labelKillAll.text = if (blockedCount > 0)
            "$blockedCount app${if (blockedCount > 1) "s" else ""} bloquée${if (blockedCount > 1) "s" else ""}"
        else
            "Appui long pour choisir les apps à bloquer"
            
        // Mode PROTÉGÉ (VIP_ONLY) : Bloque tout sauf VIP
        binding.labelVipOnly.text = if (vipCount > 0)
            "Tout bloqué sauf $vipCount VIP + système"
        else
            "Appui long pour choisir les apps VIP"
    }

    private fun isNotificationListenerEnabled(): Boolean {
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        return flat != null && flat.contains(packageName)
    }

    private fun requestNotificationAccess() {
        Toast.makeText(this, "Autorisez l'accès aux notifications", Toast.LENGTH_LONG).show()
        startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
    }
}