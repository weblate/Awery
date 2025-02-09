package ani.awery.settings

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Animatable
import android.os.Build.*
import android.os.Build.VERSION.*
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.ContextThemeWrapper
import androidx.core.content.ContextCompat
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.lifecycleScope
import ani.awery.*
import ani.awery.connections.anilist.Anilist
import ani.awery.connections.discord.Discord
import ani.awery.connections.mal.MAL
import ani.awery.databinding.ActivitySettingsBinding
import ani.awery.others.AppUpdater
import ani.awery.others.CustomBottomDialog
import ani.awery.parsers.AnimeSources
import ani.awery.parsers.MangaSources
import ani.awery.subcriptions.Notifications
import ani.awery.subcriptions.Notifications.Companion.openSettings
import ani.awery.subcriptions.Subscription.Companion.defaultTime
import ani.awery.subcriptions.Subscription.Companion.startSubscription
import ani.awery.subcriptions.Subscription.Companion.timeMinutes
import ani.awery.themes.ThemeManager
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.mrboomdev.awery.catalog.anilist.query.AnilistTagsQuery
import com.mrboomdev.awery.data.settings.AwerySettings
import com.skydoves.colorpickerview.ColorPickerView
import com.skydoves.colorpickerview.listeners.ColorListener
import eu.kanade.domain.base.BasePreferences
import eu.kanade.tachiyomi.network.NetworkPreferences
import eu.kanade.tachiyomi.util.system.toast
import io.noties.markwon.Markwon
import io.noties.markwon.SoftBreakAddsNewLinePlugin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class SettingsActivity : AppCompatActivity() {
    private val restartMainActivity = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() = startMainActivity(this@SettingsActivity)
    }
    lateinit var binding: ActivitySettingsBinding
    private val extensionInstaller = Injekt.get<BasePreferences>().extensionInstaller()
    private val networkPreferences = Injekt.get<NetworkPreferences>()

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeManager(this).applyTheme()
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initActivity(this)
        val prefs = AwerySettings.getInstance(this)

        binding.settingsVersion.text = getString(R.string.version_current, BuildConfig.VERSION_NAME)
        binding.settingsVersion.setOnLongClickListener {
            fun getArch(): String {
                SUPPORTED_ABIS.forEach {
                    when(it) {
                        "arm64-v8a" -> return "aarch64"
                        "armeabi-v7a" -> return "arm"
                        "x86_64" -> return "x86_64"
                        "x86" -> return "i686"
                    }
                }

                return System.getProperty("os.arch")
                    ?: System.getProperty("os.product.cpu.abi")
                    ?: "Unknown Architecture"
            }

            val info = """
                Awery Version: ${BuildConfig.VERSION_NAME}
                Device: $BRAND $DEVICE
                Architecture: ${getArch()}
                OS Version: $CODENAME $RELEASE ($SDK_INT)
            """.trimIndent()

            copyToClipboard(info, false)
            toast(getString(R.string.copied_device_info))
            return@setOnLongClickListener true
        }

        binding.settingsContainer.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            topMargin = statusBarHeight
            bottomMargin = navBarHeight
        }

        onBackPressedDispatcher.addCallback(this, restartMainActivity)

        binding.settingsBack.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        binding.settingsUseMaterialYou.isChecked =
            prefs.getBoolean(AwerySettings.THEME_USE_MATERIAL_YOU)

        binding.settingsUseMaterialYou.setOnCheckedChangeListener { _, isChecked ->
            prefs.setBoolean(AwerySettings.THEME_USE_MATERIAL_YOU, isChecked).saveAsync()
            if(isChecked) binding.settingsUseCustomTheme.isChecked = false
            restartApp()
        }

        binding.settingsUseCustomTheme.isChecked =
            prefs.getBoolean(AwerySettings.THEME_CUSTOM)

        binding.settingsUseCustomTheme.setOnCheckedChangeListener { _, isChecked ->
            prefs.setBoolean(AwerySettings.THEME_CUSTOM, isChecked).saveAsync()
            if(isChecked) binding.settingsUseMaterialYou.isChecked = false
            restartApp()
        }

        binding.settingsUseSourceTheme.isChecked =
            getSharedPreferences("Awery", Context.MODE_PRIVATE).getBoolean(
                "use_source_theme",
                false
            )
        binding.settingsUseSourceTheme.setOnCheckedChangeListener { _, isChecked ->
            getSharedPreferences("Awery", Context.MODE_PRIVATE).edit()
                .putBoolean("use_source_theme", isChecked).apply()
        }

        binding.settingsUseOLED.isChecked =
            getSharedPreferences("Awery", Context.MODE_PRIVATE).getBoolean("use_oled", false)
        binding.settingsUseOLED.setOnCheckedChangeListener { _, isChecked ->
            getSharedPreferences("Awery", Context.MODE_PRIVATE).edit()
                .putBoolean("use_oled", isChecked).apply()
            restartApp()
        }

        val themeString =
            getSharedPreferences("Awery", Context.MODE_PRIVATE).getString("theme", "PURPLE")!!
        binding.themeSwitcher.setText(
            themeString.substring(0, 1) + themeString.substring(1).lowercase()
        )

        binding.themeSwitcher.setAdapter(
            ArrayAdapter(
                this,
                R.layout.item_dropdown,
                ThemeManager.Companion.Theme.values()
                    .map { it.theme.substring(0, 1) + it.theme.substring(1).lowercase() })
        )

        binding.themeSwitcher.setOnItemClickListener { _, _, i, _ ->
            getSharedPreferences("Awery", Context.MODE_PRIVATE).edit()
                .putString("theme", ThemeManager.Companion.Theme.values()[i].theme).apply()
            //ActivityHelper.shouldRefreshMainActivity = true
            binding.themeSwitcher.clearFocus()
            restartApp()
        }

        binding.globalExcludedTags.setOnClickListener {
            toast("Loading tags list...", 1)

            AnilistTagsQuery.getTags(AnilistTagsQuery.ALL).executeQuery {
                val excludedTags = prefs.getStringSet(AwerySettings.CONTENT_GLOBAL_EXCLUDED_TAGS)

                runOnUiThread {
                    val layout = layoutInflater.inflate(R.layout.dialog_exclude_tags, null)
                    val chipGroup = layout.findViewById<ChipGroup>(R.id.excluded_tags_view)

                    for(tag in it) {
                        val theme = com.google.android.material.R.style.Widget_Material3_Chip_Filter
                        val chip = Chip(ContextThemeWrapper(this, theme))
                        chip.isCheckable = true
                        chip.text = tag.name
                        chipGroup.addView(chip)

                        if(excludedTags.contains(tag.name)) {
                            chip.isSelected = true
                        }

                        chip.setOnCheckedChangeListener { _, isChecked ->
                            if(isChecked) {
                                excludedTags.add(tag.name)
                                return@setOnCheckedChangeListener
                            }

                            excludedTags.remove(tag.name)
                        }

                        chip.setOnLongClickListener {
                            toast(tag.description, 1)
                            false
                        }
                    }

                    val alertDialog = AlertDialog.Builder(this, R.style.MyPopup)
                        .setTitle("Globally Exclude Tags")
                        .setView(layout)
                        .setCancelable(false)
                        .setPositiveButton("OK") { dialog, _ ->
                            prefs.setStringSet(AwerySettings.CONTENT_GLOBAL_EXCLUDED_TAGS, excludedTags)
                            prefs.saveAsync()

                            dialog.dismiss()
                            restartApp()
                        }
                        .setNegativeButton("Cancel") { dialog, _ ->
                            dialog.dismiss()
                        }
                        .create()

                    alertDialog.show()
                    alertDialog.window?.setDimAmount(0.8f)
                }
            }.catchExceptions {
                it.printStackTrace()
                toast("Failed to load tags list!")
            }
        }

        binding.customTheme.setOnClickListener {
            var passedColor = 0
            val dialogView = layoutInflater.inflate(R.layout.dialog_color_picker, null)
            val alertDialog = AlertDialog.Builder(this, R.style.MyPopup)
                .setTitle("Custom Theme")
                .setView(dialogView)
                .setPositiveButton("OK") { dialog, _ ->
                    getSharedPreferences("Awery", Context.MODE_PRIVATE).edit()
                        .putInt("custom_theme_int", passedColor).apply()
                    logger("Custom Theme: $passedColor")
                    dialog.dismiss()
                    restartApp()
                }
                .setNegativeButton("Cancel") { dialog, _ ->
                    dialog.dismiss()
                }
                .create()

            val colorPickerView = dialogView.findViewById<ColorPickerView>(R.id.colorPickerView)

            colorPickerView.setColorListener(ColorListener { color, fromUser ->
                val linearLayout = dialogView.findViewById<LinearLayout>(R.id.linear)
                passedColor = color
                linearLayout.setBackgroundColor(color)
            })

            alertDialog.show()
            alertDialog.window?.setDimAmount(0.8f)
        }

        //val animeSource = loadData<Int>("settings_def_anime_source_s")?.let { if (it >= AnimeSources.names.size) 0 else it } ?: 0
        val animeSource = getSharedPreferences(
            "Awery",
            Context.MODE_PRIVATE
        ).getInt("settings_def_anime_source_s_r", 0)
        if (AnimeSources.names.isNotEmpty() && animeSource in 0 until AnimeSources.names.size) {
            binding.animeSource.setText(AnimeSources.names[animeSource], false)
        }

        binding.animeSource.setAdapter(
            ArrayAdapter(
                this,
                R.layout.item_dropdown,
                AnimeSources.names
            )
        )

        binding.animeSource.setOnItemClickListener { _, _, i, _ ->
            //saveData("settings_def_anime_source_s", i)
            getSharedPreferences("Awery", Context.MODE_PRIVATE).edit()
                .putInt("settings_def_anime_source_s_r", i).apply()
            binding.animeSource.clearFocus()
        }

        binding.settingsPlayer.setOnClickListener {
            startActivity(Intent(this, PlayerSettingsActivity::class.java))
        }

        val managers = arrayOf("Default", "1DM", "ADM")
        val downloadManagerDialog =
            AlertDialog.Builder(this, R.style.DialogTheme).setTitle("Download Manager")

        var downloadManager = loadData<Int>("settings_download_manager") ?: 0
        binding.settingsDownloadManager.setOnClickListener {
            val dialog = downloadManagerDialog.setSingleChoiceItems(managers, downloadManager) { dialog, count ->
                downloadManager = count
                saveData("settings_download_manager", downloadManager)
                dialog.dismiss()
            }.show()
            dialog.window?.setDimAmount(0.8f)
        }

        binding.settingsForceLegacyInstall.isChecked =
            extensionInstaller.get() == BasePreferences.ExtensionInstaller.LEGACY
        binding.settingsForceLegacyInstall.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                extensionInstaller.set(BasePreferences.ExtensionInstaller.LEGACY)
            } else {
                extensionInstaller.set(BasePreferences.ExtensionInstaller.PACKAGEINSTALLER)
            }
        }

        binding.skipExtensionIcons.isChecked = loadData("skip_extension_icons") ?: false
        binding.skipExtensionIcons.setOnCheckedChangeListener { _, isChecked ->
            saveData("skip_extension_icons", isChecked)
        }
        binding.NSFWExtension.isChecked = loadData("NFSWExtension") ?: true
        binding.NSFWExtension.setOnCheckedChangeListener { _, isChecked ->
            saveData("NFSWExtension", isChecked)

        }

        binding.userAgent.setOnClickListener {
            val dialogView = layoutInflater.inflate(R.layout.dialog_user_agent, null)
            val editText = dialogView.findViewById<TextInputEditText>(R.id.userAgentTextBox)
            editText.setText(networkPreferences.defaultUserAgent().get())
            val alertDialog = AlertDialog.Builder(this, R.style.MyPopup)
                .setTitle("User Agent")
                .setView(dialogView)
                .setPositiveButton("OK") { dialog, _ ->
                    networkPreferences.defaultUserAgent().set(editText.text.toString())
                    dialog.dismiss()
                }
                .setNeutralButton("Reset") { dialog, _ ->
                    networkPreferences.defaultUserAgent()
                        .set("Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:110.0) Gecko/20100101 Firefox/110.0") // Reset to default or empty
                    editText.setText("")
                    dialog.dismiss()
                }
                .setNegativeButton("Cancel") { dialog, _ ->
                    dialog.dismiss()
                }
                .create()

            alertDialog.show()
            alertDialog.window?.setDimAmount(0.8f)
        }


        val exDns = listOf(
            "None",
            "Cloudflare",
            "Google",
            "AdGuard",
            "Quad9",
            "AliDNS",
            "DNSPod",
            "360",
            "Quad101",
            "Mullvad",
            "Controld",
            "Njalla",
            "Shecan",
            "Libre"
        )

        binding.settingsExtensionDns.setText(exDns[networkPreferences.dohProvider().get()], false)
        binding.settingsExtensionDns.setAdapter(ArrayAdapter(this, R.layout.item_dropdown, exDns))
        binding.settingsExtensionDns.setOnItemClickListener { _, _, i, _ ->
            networkPreferences.dohProvider().set(i)
            binding.settingsExtensionDns.clearFocus()
            Toast.makeText(this, "Restart app to apply changes", Toast.LENGTH_LONG).show()
        }

        binding.settingsDownloadInSd.isChecked = loadData("sd_dl") ?: false
        binding.settingsDownloadInSd.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                val arrayOfFiles = ContextCompat.getExternalFilesDirs(this, null)
                if (arrayOfFiles.size > 1 && arrayOfFiles[1] != null) {
                    saveData("sd_dl", true)
                } else {
                    binding.settingsDownloadInSd.isChecked = false
                    saveData("sd_dl", false)
                    snackString(getString(R.string.noSdFound))
                }
            } else saveData("sd_dl", false)
        }

        binding.settingsContinueMedia.isChecked = loadData("continue_media") ?: true
        binding.settingsContinueMedia.setOnCheckedChangeListener { _, isChecked ->
            saveData("continue_media", isChecked)
        }

        binding.settingsRecentlyListOnly.isChecked = loadData("recently_list_only") ?: false
        binding.settingsRecentlyListOnly.setOnCheckedChangeListener { _, isChecked ->
            saveData("recently_list_only", isChecked)
        }
        binding.settingsPreferDub.isChecked = loadData("settings_prefer_dub") ?: false
        binding.settingsPreferDub.setOnCheckedChangeListener { _, isChecked ->
            saveData("settings_prefer_dub", isChecked)
        }

        //val mangaSource = loadData<Int>("settings_def_manga_source_s")?.let { if (it >= MangaSources.names.size) 0 else it } ?: 0
        val mangaSource = getSharedPreferences(
            "Awery",
            Context.MODE_PRIVATE
        ).getInt("settings_def_manga_source_s_r", 0)
        if (MangaSources.names.isNotEmpty() && mangaSource in 0 until MangaSources.names.size) {
            binding.mangaSource.setText(MangaSources.names[mangaSource], false)
        }

        // Set up the dropdown adapter.
        binding.mangaSource.setAdapter(
            ArrayAdapter(
                this,
                R.layout.item_dropdown,
                MangaSources.names
            )
        )

        // Set up the item click listener for the dropdown.
        binding.mangaSource.setOnItemClickListener { _, _, i, _ ->
            //saveData("settings_def_manga_source_s", i)
            getSharedPreferences("Awery", Context.MODE_PRIVATE).edit()
                .putInt("settings_def_manga_source_s_r", i).apply()
            binding.mangaSource.clearFocus()
        }

        binding.settingsReader.setOnClickListener {
            startActivity(Intent(this, ReaderSettingsActivity::class.java))
        }

        val uiSettings: UserInterfaceSettings =
            loadData("ui_settings", toast = false)
                ?: UserInterfaceSettings().apply { saveData("ui_settings", this) }
        var previous: View = when (uiSettings.darkMode) {
            null -> binding.settingsUiAuto
            true -> binding.settingsUiDark
            false -> binding.settingsUiLight
        }
        previous.alpha = 1f
        fun uiTheme(mode: Boolean?, current: View) {
            previous.alpha = 0.33f
            previous = current
            current.alpha = 1f
            uiSettings.darkMode = mode
            saveData("ui_settings", uiSettings)
            Refresh.all()
            finish()
            startActivity(Intent(this, SettingsActivity::class.java))
            initActivity(this)
        }

        binding.settingsUiAuto.setOnClickListener {
            uiTheme(null, it)
        }

        binding.settingsUiLight.setOnClickListener {
            binding.settingsUseOLED.isChecked = false
            uiTheme(false, it)
        }

        binding.settingsUiDark.setOnClickListener {
            uiTheme(true, it)
        }

        binding.settingsIncognito.isChecked =
            getSharedPreferences("Awery", Context.MODE_PRIVATE).getBoolean(
                "incognito",
                false
            )
        binding.settingsIncognito.setOnCheckedChangeListener { _, isChecked ->
            getSharedPreferences("Awery", Context.MODE_PRIVATE).edit()
                .putBoolean("incognito", isChecked).apply()
        }

        var previousStart: View = when (uiSettings.defaultStartUpTab) {
            0 -> binding.uiSettingsAnime
            1 -> binding.uiSettingsHome
            2 -> binding.uiSettingsManga
            else -> binding.uiSettingsHome
        }
        previousStart.alpha = 1f
        fun uiTheme(mode: Int, current: View) {
            previousStart.alpha = 0.33f
            previousStart = current
            current.alpha = 1f
            uiSettings.defaultStartUpTab = mode
            saveData("ui_settings", uiSettings)
            initActivity(this)
        }

        binding.uiSettingsAnime.setOnClickListener {
            uiTheme(0, it)
        }

        binding.uiSettingsHome.setOnClickListener {
            uiTheme(1, it)
        }

        binding.uiSettingsManga.setOnClickListener {
            uiTheme(2, it)
        }

        binding.settingsShowYt.isChecked = uiSettings.showYtButton
        binding.settingsShowYt.setOnCheckedChangeListener { _, isChecked ->
            uiSettings.showYtButton = isChecked
            saveData("ui_settings", uiSettings)
        }

        var previousEp: View = when (uiSettings.animeDefaultView) {
            0 -> binding.settingsEpList
            1 -> binding.settingsEpGrid
            2 -> binding.settingsEpCompact
            else -> binding.settingsEpList
        }
        previousEp.alpha = 1f
        fun uiEp(mode: Int, current: View) {
            previousEp.alpha = 0.33f
            previousEp = current
            current.alpha = 1f
            uiSettings.animeDefaultView = mode
            saveData("ui_settings", uiSettings)
        }

        binding.settingsEpList.setOnClickListener {
            uiEp(0, it)
        }

        binding.settingsEpGrid.setOnClickListener {
            uiEp(1, it)
        }

        binding.settingsEpCompact.setOnClickListener {
            uiEp(2, it)
        }

        var previousChp: View = when (uiSettings.mangaDefaultView) {
            0 -> binding.settingsChpList
            1 -> binding.settingsChpCompact
            else -> binding.settingsChpList
        }
        previousChp.alpha = 1f
        fun uiChp(mode: Int, current: View) {
            previousChp.alpha = 0.33f
            previousChp = current
            current.alpha = 1f
            uiSettings.mangaDefaultView = mode
            saveData("ui_settings", uiSettings)
        }

        binding.settingsChpList.setOnClickListener {
            uiChp(0, it)
        }

        binding.settingsChpCompact.setOnClickListener {
            uiChp(1, it)
        }

        binding.settingsUi.setOnClickListener {
            startActivity(Intent(this, UserInterfaceSettingsActivity::class.java))
        }

        (binding.settingsLogo.drawable as Animatable).start()
        val array = resources.getStringArray(R.array.tips)

        binding.settingsLogo.setSafeOnClickListener {
            (binding.settingsLogo.drawable as Animatable).start()
            snackString(array[(Math.random() * array.size).toInt()], this)
        }

        binding.settingsDev.setOnClickListener {
            DevelopersDialogFragment().show(supportFragmentManager, "dialog")
        }

        binding.settingsDisclaimer.setOnClickListener {
            val title = getString(R.string.disclaimer)
            val text = TextView(this)
            text.setText(R.string.full_disclaimer)

            CustomBottomDialog.newInstance().apply {
                setTitleText(title)
                addView(text)
                setNegativeButton(currContext()!!.getString(R.string.close)) {
                    dismiss()
                }
                show(supportFragmentManager, "dialog")
            }
        }

        var curTime = loadData<Int>("subscriptions_time_s") ?: defaultTime
        val timeNames = timeMinutes.map {
            val mins = it % 60
            val hours = it / 60
            if (it > 0) "${if (hours > 0) "$hours hrs " else ""}${if (mins > 0) "$mins mins" else ""}"
            else getString(R.string.do_not_update)
        }.toTypedArray()
        binding.settingsSubscriptionsTime.text =
            getString(R.string.subscriptions_checking_time_s, timeNames[curTime])
        val speedDialog = AlertDialog.Builder(this, R.style.DialogTheme)
            .setTitle(R.string.subscriptions_checking_time)
        binding.settingsSubscriptionsTime.setOnClickListener {
            val dialog = speedDialog.setSingleChoiceItems(timeNames, curTime) { dialog, i ->
                curTime = i
                binding.settingsSubscriptionsTime.text =
                    getString(R.string.subscriptions_checking_time_s, timeNames[i])
                saveData("subscriptions_time_s", curTime)
                dialog.dismiss()
                startSubscription(true)
            }.show()
            dialog.window?.setDimAmount(0.8f)
        }

        binding.settingsSubscriptionsTime.setOnLongClickListener {
            startSubscription(true)
            true
        }

        binding.settingsNotificationsCheckingSubscriptions.isChecked =
            loadData("subscription_checking_notifications") ?: true
        binding.settingsNotificationsCheckingSubscriptions.setOnCheckedChangeListener { _, isChecked ->
            saveData("subscription_checking_notifications", isChecked)
            if (isChecked)
                Notifications.createChannel(
                    this,
                    null,
                    "subscription_checking",
                    getString(R.string.checking_subscriptions),
                    false
                )
            else
                Notifications.deleteChannel(this, "subscription_checking")
        }

        binding.settingsNotificationsCheckingSubscriptions.setOnLongClickListener {
            openSettings(this, null)
        }


        binding.settingsCheckUpdate.isChecked = loadData("check_update") ?: true
        binding.settingsCheckUpdate.setOnCheckedChangeListener { _, isChecked ->
            saveData("check_update", isChecked)
            if (!isChecked) {
                snackString(getString(R.string.long_click_to_check_update))
            }
        }

        binding.settingsLogo.setOnLongClickListener {
            lifecycleScope.launch(Dispatchers.IO) {
                AppUpdater.check(this@SettingsActivity, true)
            }
            true
        }

        binding.settingsCheckUpdate.setOnLongClickListener {
            lifecycleScope.launch(Dispatchers.IO) {
                AppUpdater.check(this@SettingsActivity, true)
            }
            true
        }

        binding.settingsAccountHelp.setOnClickListener {
            val title = getString(R.string.account_help)
            val full = getString(R.string.full_account_help)
            CustomBottomDialog.newInstance().apply {
                setTitleText(title)
                addView(
                    TextView(it.context).apply {
                        val markWon = Markwon.builder(it.context)
                            .usePlugin(SoftBreakAddsNewLinePlugin.create()).build()
                        markWon.setMarkdown(this, full)
                    }
                )
            }.show(supportFragmentManager, "dialog")
        }

        fun reload() {
            if (Anilist.token != null) {
                binding.settingsAnilistLogin.setText(R.string.logout)
                binding.settingsAnilistLogin.setOnClickListener {
                    Anilist.removeSavedToken(it.context)
                    restartMainActivity.isEnabled = true
                    reload()
                }
                binding.settingsAnilistUsername.visibility = View.VISIBLE
                binding.settingsAnilistUsername.text = Anilist.username
                binding.settingsAnilistAvatar.loadImage(Anilist.avatar)

                binding.settingsMALLoginRequired.visibility = View.GONE
                binding.settingsMALLogin.visibility = View.VISIBLE
                binding.settingsMALUsername.visibility = View.VISIBLE

                if (MAL.token != null) {
                    binding.settingsMALLogin.setText(R.string.logout)
                    binding.settingsMALLogin.setOnClickListener {
                        MAL.removeSavedToken(it.context)
                        restartMainActivity.isEnabled = true
                        reload()
                    }
                    binding.settingsMALUsername.visibility = View.VISIBLE
                    binding.settingsMALUsername.text = MAL.username
                    binding.settingsMALAvatar.loadImage(MAL.avatar)
                } else {
                    binding.settingsMALAvatar.setImageResource(R.drawable.ic_round_person_24)
                    binding.settingsMALUsername.visibility = View.GONE
                    binding.settingsMALLogin.setText(R.string.login)
                    binding.settingsMALLogin.setOnClickListener {
                        MAL.loginIntent(this)
                    }
                }
            } else {
                binding.settingsAnilistAvatar.setImageResource(R.drawable.ic_round_person_24)
                binding.settingsAnilistUsername.visibility = View.GONE
                binding.settingsAnilistLogin.setText(R.string.login)
                binding.settingsAnilistLogin.setOnClickListener {
                    Anilist.loginIntent(this)
                }
                binding.settingsMALLoginRequired.visibility = View.VISIBLE
                binding.settingsMALLogin.visibility = View.GONE
                binding.settingsMALUsername.visibility = View.GONE
            }

            if (Discord.token != null) {
                val id = getSharedPreferences(
                    "aweryprefs",
                    Context.MODE_PRIVATE
                ).getString("discord_id", null)
                val avatar = getSharedPreferences(
                    "aweryprefs",
                    Context.MODE_PRIVATE
                ).getString("discord_avatar", null)
                val username = getSharedPreferences(
                    "aweryprefs",
                    Context.MODE_PRIVATE
                ).getString("discord_username", null)
                if (id != null && avatar != null) {
                    binding.settingsDiscordAvatar.loadImage("https://cdn.discordapp.com/avatars/$id/$avatar.png")
                }
                binding.settingsDiscordUsername.visibility = View.VISIBLE
                binding.settingsDiscordUsername.text =
                    username ?: Discord.token?.replace(Regex("."), "*")
                binding.settingsDiscordLogin.setText(R.string.logout)
                binding.settingsDiscordLogin.setOnClickListener {
                    Discord.removeSavedToken(this)
                    restartMainActivity.isEnabled = true
                    reload()
                }
            } else {
                binding.settingsDiscordAvatar.setImageResource(R.drawable.ic_round_person_24)
                binding.settingsDiscordUsername.visibility = View.GONE
                binding.settingsDiscordLogin.setText(R.string.login)
                binding.settingsDiscordLogin.setOnClickListener {
                    Discord.warning(this).show(supportFragmentManager, "dialog")
                }
            }
        }

        reload()
    }

    private fun restartApp() {
        Snackbar.make(
            binding.root,
            R.string.restart_app, Snackbar.LENGTH_SHORT
        ).apply {
            val mainIntent =
                Intent.makeRestartActivityTask(
                    context.packageManager.getLaunchIntentForPackage(
                        context.packageName
                    )!!.component
                )
            setAction("Do it!") {
                context.startActivity(mainIntent)
                Runtime.getRuntime().exit(0)
            }
            show()
        }
    }
}