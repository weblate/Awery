package ani.awery.settings

import android.app.AlertDialog
import android.app.NotificationManager
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import ani.awery.R
import ani.awery.databinding.FragmentAnimeExtensionsBinding
import ani.awery.loadData
import ani.awery.others.LanguageMapper
import ani.awery.settings.extensionprefs.AnimeSourcePreferencesFragment
import ani.awery.snackString
import com.google.android.material.tabs.TabLayout
import com.google.android.material.textfield.TextInputLayout
import eu.kanade.tachiyomi.animesource.ConfigurableAnimeSource
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.extension.anime.AnimeExtensionManager
import eu.kanade.tachiyomi.extension.anime.model.AnimeExtension
import kotlinx.coroutines.launch
import rx.android.schedulers.AndroidSchedulers
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.Locale

class InstalledAnimeExtensionsFragment : Fragment(), SearchQueryHandler {
    private var _binding: FragmentAnimeExtensionsBinding? = null
    private val binding get() = _binding!!
    private lateinit var extensionsRecyclerView: RecyclerView
    val skipIcons = loadData("skip_extension_icons") ?: false
    private val animeExtensionManager: AnimeExtensionManager = Injekt.get()
    private val extensionsAdapter = AnimeExtensionsAdapter(
        { pkg ->
            val allSettings = pkg.sources.filterIsInstance<ConfigurableAnimeSource>()
            if (allSettings.isNotEmpty()) {
                var selectedSetting = allSettings[0]
                if (allSettings.size > 1) {
                    val names = allSettings.map { it.lang }.toTypedArray()
                    var selectedIndex = 0
                    val dialog = AlertDialog.Builder(requireContext(), R.style.MyPopup)
                        .setTitle("Select a Source")
                        .setSingleChoiceItems(names, selectedIndex) { dialog, which ->
                            selectedIndex = which
                            selectedSetting = allSettings[selectedIndex]
                            dialog.dismiss()

                            // Move the fragment transaction here
                            val eActivity = requireActivity() as ExtensionsActivity
                            eActivity.runOnUiThread {
                                val fragment =
                                    AnimeSourcePreferencesFragment().getInstance(selectedSetting.id) {

                                        eActivity.findViewById<ViewPager2>(R.id.viewPager).visibility =
                                            View.VISIBLE
                                        eActivity.findViewById<TabLayout>(R.id.tabLayout).visibility =
                                            View.VISIBLE
                                        eActivity.findViewById<TextInputLayout>(R.id.searchView).visibility =
                                            View.VISIBLE
                                        eActivity.findViewById<FrameLayout>(R.id.fragmentExtensionsContainer).visibility =
                                            View.GONE
                                    }
                                parentFragmentManager.beginTransaction()
                                    .setCustomAnimations(R.anim.slide_up, R.anim.slide_down)
                                    .replace(R.id.fragmentExtensionsContainer, fragment)
                                    .addToBackStack(null)
                                    .commit()
                            }
                        }
                        .show()
                    dialog.window?.setDimAmount(0.8f)
                } else {
                    // If there's only one setting, proceed with the fragment transaction
                    val eActivity = requireActivity() as ExtensionsActivity
                    eActivity.runOnUiThread {
                        val fragment =
                            AnimeSourcePreferencesFragment().getInstance(selectedSetting.id) {

                                eActivity.findViewById<ViewPager2>(R.id.viewPager).visibility =
                                    View.VISIBLE
                                eActivity.findViewById<TabLayout>(R.id.tabLayout).visibility =
                                    View.VISIBLE
                                eActivity.findViewById<TextInputLayout>(R.id.searchView).visibility =
                                    View.VISIBLE
                                eActivity.findViewById<FrameLayout>(R.id.fragmentExtensionsContainer).visibility =
                                    View.GONE
                            }
                        parentFragmentManager.beginTransaction()
                            .setCustomAnimations(R.anim.slide_up, R.anim.slide_down)
                            .replace(R.id.fragmentExtensionsContainer, fragment)
                            .addToBackStack(null)
                            .commit()
                    }
                }

                // Hide ViewPager2 and TabLayout
                val activity = requireActivity() as ExtensionsActivity
                activity.findViewById<ViewPager2>(R.id.viewPager).visibility = View.GONE
                activity.findViewById<TabLayout>(R.id.tabLayout).visibility = View.GONE
                activity.findViewById<TextInputLayout>(R.id.searchView).visibility = View.GONE
                activity.findViewById<FrameLayout>(R.id.fragmentExtensionsContainer).visibility =
                    View.VISIBLE
            } else {
                Toast.makeText(requireContext(), "Source is not configurable", Toast.LENGTH_SHORT)
                    .show()
            }
        },
        { pkg ->
            if (isAdded) {  // Check if the fragment is currently added to its activity
                val context = requireContext()  // Store context in a variable
                val notificationManager =
                    context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager  // Initialize NotificationManager once

                if (pkg.hasUpdate) {
                    animeExtensionManager.updateExtension(pkg)
                        .observeOn(AndroidSchedulers.mainThread())  // Observe on main thread
                        .subscribe(
                            { installStep ->
                                val builder = NotificationCompat.Builder(
                                    context,
                                    Notifications.CHANNEL_DOWNLOADER_PROGRESS
                                )
                                    .setSmallIcon(R.drawable.ic_round_sync_24)
                                    .setContentTitle("Updating extension")
                                    .setContentText("Step: $installStep")
                                    .setPriority(NotificationCompat.PRIORITY_LOW)
                                notificationManager.notify(1, builder.build())
                            },
                            { error ->
                                Log.e("AnimeExtensionsAdapter", "Error: ", error)  // Log the error
                                val builder = NotificationCompat.Builder(
                                    context,
                                    Notifications.CHANNEL_DOWNLOADER_ERROR
                                )
                                    .setSmallIcon(R.drawable.ic_round_info_24)
                                    .setContentTitle("Update failed: ${error.message}")
                                    .setContentText("Error: ${error.message}")
                                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                                notificationManager.notify(1, builder.build())
                                snackString("Update failed: ${error.message}")
                            },
                            {
                                val builder = NotificationCompat.Builder(
                                    context,
                                    Notifications.CHANNEL_DOWNLOADER_PROGRESS
                                )
                                    .setSmallIcon(R.drawable.ic_circle_check)
                                    .setContentTitle("Update complete")
                                    .setContentText("The extension has been successfully updated.")
                                    .setPriority(NotificationCompat.PRIORITY_LOW)
                                notificationManager.notify(1, builder.build())
                                snackString("Extension updated")
                            }
                        )
                } else {
                    animeExtensionManager.uninstallExtension(pkg.pkgName)
                    snackString("Extension uninstalled")
                }
            }
        }, skipIcons
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAnimeExtensionsBinding.inflate(inflater, container, false)

        extensionsRecyclerView = binding.allAnimeExtensionsRecyclerView
        extensionsRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        extensionsRecyclerView.adapter = extensionsAdapter


        lifecycleScope.launch {
            animeExtensionManager.installedExtensionsFlow.collect { extensions ->
                extensionsAdapter.updateData(extensions)
            }
        }
        val extensionsRecyclerView: RecyclerView = binding.allAnimeExtensionsRecyclerView
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView();_binding = null
    }

    override fun updateContentBasedOnQuery(query: String?) {
        extensionsAdapter.filter(query ?: "", animeExtensionManager.installedExtensionsFlow.value)
    }

    private class AnimeExtensionsAdapter(
        private val onSettingsClicked: (AnimeExtension.Installed) -> Unit,
        private val onUninstallClicked: (AnimeExtension.Installed) -> Unit,
        val skipIcons: Boolean
    ) : ListAdapter<AnimeExtension.Installed, AnimeExtensionsAdapter.ViewHolder>(
        DIFF_CALLBACK_INSTALLED
    ) {

        fun updateData(newExtensions: List<AnimeExtension.Installed>) {
            submitList(newExtensions)  // Use submitList instead of manual list handling
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_extension, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val extension = getItem(position) // Use getItem() from ListAdapter
            val nsfw = if (extension.isNsfw) "(18+)" else ""
            val lang = LanguageMapper.mapLanguageCodeToName(extension.lang)
            holder.extensionNameTextView.text = extension.name
            holder.extensionVersionTextView.text = "$lang ${extension.versionName} $nsfw"
            if (!skipIcons) {
                holder.extensionIconImageView.setImageDrawable(extension.icon)
            }
            if (extension.hasUpdate) {
                holder.closeTextView.setImageResource(R.drawable.ic_round_sync_24)
            } else {
                holder.closeTextView.setImageResource(R.drawable.ic_round_delete_24)
            }
            holder.closeTextView.setOnClickListener {
                onUninstallClicked(extension)
            }
            holder.settingsImageView.setOnClickListener {
                onSettingsClicked(extension)
            }
        }

        fun filter(query: String, currentList: List<AnimeExtension.Installed>) {
            val filteredList = ArrayList<AnimeExtension.Installed>()
            for (extension in currentList) {
                if (extension.name.lowercase(Locale.ROOT).contains(query.lowercase(Locale.ROOT))) {
                    filteredList.add(extension)
                }
            }
            submitList(filteredList)
        }

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val extensionNameTextView: TextView = view.findViewById(R.id.extensionNameTextView)
            val extensionVersionTextView: TextView =
                view.findViewById(R.id.extensionVersionTextView)
            val settingsImageView: ImageView = view.findViewById(R.id.settingsImageView)
            val extensionIconImageView: ImageView = view.findViewById(R.id.extensionIconImageView)
            val closeTextView: ImageView = view.findViewById(R.id.closeTextView)
        }

        companion object {
            val DIFF_CALLBACK_INSTALLED =
                object : DiffUtil.ItemCallback<AnimeExtension.Installed>() {
                    override fun areItemsTheSame(
                        oldItem: AnimeExtension.Installed,
                        newItem: AnimeExtension.Installed
                    ): Boolean {
                        return oldItem.pkgName == newItem.pkgName
                    }

                    override fun areContentsTheSame(
                        oldItem: AnimeExtension.Installed,
                        newItem: AnimeExtension.Installed
                    ): Boolean {
                        return oldItem == newItem
                    }
                }
        }
    }

}