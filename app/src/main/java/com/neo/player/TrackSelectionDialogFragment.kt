package com.neo.player

import android.app.Dialog
import android.os.Bundle
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.ViewModelProvider
import androidx.media3.common.C
import android.widget.Toast
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class TrackSelectionDialogFragment : DialogFragment() {

    companion object {
        private const val ARG_TRACK_TYPE = "track_type"

        fun newInstance(type: @C.TrackType Int): TrackSelectionDialogFragment {
            return TrackSelectionDialogFragment().apply {
                arguments = Bundle().apply {
                    putInt(ARG_TRACK_TYPE, type)
                }
            }
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val type = requireArguments().getInt(ARG_TRACK_TYPE)
        val viewModel = ViewModelProvider(requireActivity())[PlayerViewModel::class.java]

        val titleRes =
            when (type) {
                C.TRACK_TYPE_AUDIO -> com.neo.neomovies.R.string.select_audio_track
                C.TRACK_TYPE_TEXT -> com.neo.neomovies.R.string.select_subtitle_track
                C.TRACK_TYPE_VIDEO -> com.neo.neomovies.R.string.select_video_quality
                else -> error("TrackType must be AUDIO, TEXT or VIDEO")
            }

        val tracks = viewModel.getSelectableTracks(type)
        val items =
            arrayOf(getString(com.neo.neomovies.R.string.none)) +
                tracks.map { t -> if (t.isSupported) t.label else "${t.label} (unsupported)" }.toTypedArray()
        val checked =
            (tracks.indexOfFirst { it.isSelected }.takeIf { it >= 0 }?.plus(1))
                ?: 0

        return requireActivity().let { activity ->
            MaterialAlertDialogBuilder(activity)
                .setTitle(getString(titleRes))
                .setSingleChoiceItems(
                    items,
                    checked,
                ) { dialog, which ->
                    if (which == 0) {
                        viewModel.switchToTrack(type, -1)
                        dialog.dismiss()
                        return@setSingleChoiceItems
                    }

                    val selected = tracks.getOrNull(which - 1)
                    if (selected == null) return@setSingleChoiceItems

                    if (!selected.isSupported) {
                        Toast.makeText(activity, "Track is not supported by ExoPlayer on this device", Toast.LENGTH_SHORT)
                            .show()
                        return@setSingleChoiceItems
                    }

                    viewModel.switchToTrack(type, which - 1)
                    dialog.dismiss()
                }
                .create()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        activity?.window?.let {
            WindowCompat.getInsetsController(it, it.decorView).apply {
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                hide(WindowInsetsCompat.Type.systemBars())
            }
        }
    }
}
