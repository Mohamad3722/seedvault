/*
 * SPDX-FileCopyrightText: 2021 The Calyx Institute
 * SPDX-License-Identifier: Apache-2.0
 */

package de.grobox.storagebackuptester

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.View.INVISIBLE
import android.view.View.VISIBLE
import android.view.ViewGroup
import android.widget.Button
import android.widget.ProgressBar
import android.widget.Toast
import android.widget.Toast.LENGTH_SHORT
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.RecyclerView
import de.grobox.storagebackuptester.settings.SettingsFragment

private const val EMAIL = "incoming+grote-storage-backup-tester-22079635-issue-@incoming.gitlab.com"

open class LogFragment : Fragment() {

    companion object {
        fun newInstance(): LogFragment = LogFragment()
    }

    private val viewModel: MainViewModel by activityViewModels()

    private lateinit var list: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var horizontalProgressBar: ProgressBar
    private lateinit var button: Button
    private val adapter = LogAdapter()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val v = inflater.inflate(R.layout.fragment_log, container, false)
        list = v.findViewById(R.id.listView)
        list.adapter = adapter
        progressBar = v.findViewById(R.id.progressBar)
        horizontalProgressBar = v.findViewById(R.id.horizontalProgressBar)
        button = v.findViewById(R.id.button)
        viewModel.backupLog.observe(viewLifecycleOwner) { progress ->
            progress.text?.let { adapter.addItem(it) }
            horizontalProgressBar.max = progress.total
            horizontalProgressBar.setProgress(progress.current, true)
            list.postDelayed({
                list.scrollToPosition(adapter.itemCount - 1)
            }, 50)
        }
        viewModel.backupButtonEnabled.observe(viewLifecycleOwner) { enabled ->
            button.isEnabled = enabled
            progressBar.visibility = if (enabled) INVISIBLE else VISIBLE
            if (!enabled) adapter.clear()
        }
        button.setOnClickListener {
            if (!checkPermission()) return@setOnClickListener
            viewModel.simulateBackup()
        }
        return v
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.requireViewById<Toolbar>(R.id.toolbar).apply {
            title = getString(R.string.app_name)
            setOnMenuItemClickListener(::onMenuItemSelected)
        }
    }

    private fun onMenuItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.settings -> {
                if (!checkPermission()) return false
                parentFragmentManager.beginTransaction()
                    .replace(R.id.container, SettingsFragment.newInstance())
                    .addToBackStack("SETTINGS")
                    .commit()
                true
            }
            R.id.share -> {
                val subject = adapter.items.takeLast(2).joinToString(" - ").replace("\n", "")
                val text = adapter.items.takeLast(333).joinToString("\n")
                val sendIntent: Intent = Intent().apply {
                    action = Intent.ACTION_SEND
                    putExtra(Intent.EXTRA_EMAIL, arrayOf(EMAIL))
                    putExtra(Intent.EXTRA_SUBJECT, subject)
                    putExtra(Intent.EXTRA_TEXT, text)
                    type = "text/plain"
                }
                val shareIntent = Intent.createChooser(sendIntent, null)
                startActivity(shareIntent)
                true
            }
            else -> false
        }
    }

    private fun checkPermission(): Boolean {
        if (Environment.isExternalStorageManager()) return true
        Toast.makeText(requireContext(), "Permission needed", LENGTH_SHORT).show()
        val i = Intent(ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
            data = Uri.parse("package:${requireContext().packageName}")
        }
        startActivity(i)
        return false
    }

}
