package moe.shizuku.manager.update

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.fragment.app.DialogFragment
import moe.shizuku.manager.R
import moe.shizuku.manager.databinding.DialogUpdateBinding

class UpdateDialogFragment : DialogFragment() {

    companion object {
        const val TAG = "UpdateDialog"
        private const val ARG_VERSION = "version"
        private const val ARG_NOTES = "notes"
        private const val ARG_URL = "url"

        fun newInstance(info: UpdateInfo) = UpdateDialogFragment().apply {
            arguments = Bundle().apply {
                putString(ARG_VERSION, info.versionName)
                putString(ARG_NOTES, info.releaseNotes)
                putString(ARG_URL, info.downloadUrl)
            }
        }
    }

    private var _binding: DialogUpdateBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = DialogUpdateBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val version = arguments?.getString(ARG_VERSION) ?: ""
        val notes = arguments?.getString(ARG_NOTES) ?: ""
        val url = arguments?.getString(ARG_URL) ?: ""

        binding.updateVersionLabel.text = getString(R.string.update_dialog_version, version)
        binding.updateReleaseNotes.text = notes.ifBlank { getString(R.string.update_dialog_no_notes) }

        binding.btnUpdateLater.setOnClickListener { dismiss() }
        binding.btnUpdateNow.setOnClickListener {
            dismiss()
            val info = UpdateInfo(
                versionName = version,
                versionCode = 0,
                releaseNotes = notes,
                downloadUrl = url,
                publishedAt = ""
            )
            UpdateInstaller.downloadAndInstall(requireContext(), info)
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return super.onCreateDialog(savedInstanceState).apply {
            window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.apply {
            setLayout(
                (resources.displayMetrics.widthPixels * 0.9).toInt(),
                WindowManager.LayoutParams.WRAP_CONTENT
            )
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
