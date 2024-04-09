/*
 * Copyright (c) 2010-2023 Belledonne Communications SARL.
 *
 * This file is part of linphone-android
 * (see https://www.linphone.org).
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.linphone.ui.main.meetings.fragment

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.UiThread
import androidx.core.content.ContextCompat
import androidx.core.view.doOnPreDraw
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.R
import org.linphone.core.tools.Log
import org.linphone.databinding.MeetingWaitingRoomFragmentBinding
import org.linphone.ui.call.fragment.AudioDevicesMenuDialogFragment
import org.linphone.ui.call.model.AudioDeviceModel
import org.linphone.ui.main.MainActivity
import org.linphone.ui.main.fragment.GenericFragment
import org.linphone.ui.main.meetings.viewmodel.MeetingWaitingRoomViewModel

@UiThread
class MeetingWaitingRoomFragment : GenericFragment() {
    companion object {
        private const val TAG = "[Meeting Waiting Room Fragment]"
    }

    private lateinit var binding: MeetingWaitingRoomFragmentBinding

    private lateinit var viewModel: MeetingWaitingRoomViewModel

    private val args: MeetingWaitingRoomFragmentArgs by navArgs()

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Log.i("$TAG Camera permission has been granted")
            enableVideoPreview()
        } else {
            Log.e("$TAG Camera permission has been denied, leaving this fragment")
            goBack()
        }
    }

    private var bottomSheetDialog: BottomSheetDialogFragment? = null

    private var navBarDefaultColor: Int = -1

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = MeetingWaitingRoomFragmentBinding.inflate(layoutInflater)
        return binding.root
    }

    override fun goBack(): Boolean {
        return findNavController().popBackStack()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        postponeEnterTransition()
        super.onViewCreated(view, savedInstanceState)

        navBarDefaultColor = requireActivity().window.navigationBarColor

        binding.lifecycleOwner = viewLifecycleOwner

        viewModel = ViewModelProvider(this)[MeetingWaitingRoomViewModel::class.java]
        binding.viewModel = viewModel

        val uri = args.conferenceUri
        Log.i(
            "$TAG Looking up for conference with SIP URI [$uri]"
        )
        viewModel.findConferenceInfo(uri)

        binding.setBackClickListener {
            goBack()
        }

        viewModel.showAudioDevicesListEvent.observe(viewLifecycleOwner) {
            it.consume { devices ->
                showAudioRoutesMenu(devices)
            }
        }

        viewModel.conferenceInfoFoundEvent.observe(viewLifecycleOwner) {
            it.consume { found ->
                if (found) {
                    (view.parent as? ViewGroup)?.doOnPreDraw {
                        startPostponedEnterTransition()
                    }
                } else {
                    Log.e("$TAG Failed to find meeting with URI [$uri], going back")
                    (view.parent as? ViewGroup)?.doOnPreDraw {
                        goBack()
                    }
                }
            }
        }

        viewModel.conferenceCreatedEvent.observe(viewLifecycleOwner) {
            it.consume {
                Log.i("$TAG Conference was joined, leaving waiting room")
                goBack()
            }
        }

        viewModel.conferenceCreationError.observe(viewLifecycleOwner) {
            it.consume {
                Log.e("$TAG Error joining the conference!")
                val message = getString(
                    R.string.toast_no_app_registered_to_handle_content_type_error
                )
                val icon = R.drawable.x
                (requireActivity() as MainActivity).showRedToast(message, icon)
            }
        }

        if (!isCameraPermissionGranted()) {
            viewModel.isVideoAvailable.value = false
            Log.w("$TAG Camera permission wasn't granted yet, asking for it now")
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    override fun onResume() {
        // Force this navigation bar color
        requireActivity().window.navigationBarColor = requireContext().getColor(R.color.gray_900)

        super.onResume()

        if (isCameraPermissionGranted()) {
            Log.i(
                "$TAG Record video permission is granted, starting video preview with front cam if possible"
            )
            viewModel.setFrontCamera()
            enableVideoPreview()
        }
    }

    override fun onPause() {
        bottomSheetDialog?.dismiss()
        bottomSheetDialog = null

        coreContext.postOnCoreThread { core ->
            core.nativePreviewWindowId = null
            core.isVideoPreviewEnabled = false
        }

        super.onPause()
    }

    override fun onDestroy() {
        // Reset default navigation bar color
        requireActivity().window.navigationBarColor = navBarDefaultColor

        super.onDestroy()
    }

    private fun isCameraPermissionGranted(): Boolean {
        val granted = ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        Log.i("$TAG Camera permission is ${if (granted) "granted" else "denied"}")
        return granted
    }

    private fun enableVideoPreview() {
        coreContext.postOnCoreThread { core ->
            if (core.isVideoEnabled) {
                viewModel.isVideoAvailable.postValue(true)
                core.nativePreviewWindowId = binding.videoPreview
                core.isVideoPreviewEnabled = true
            }
        }
    }

    private fun showAudioRoutesMenu(devicesList: List<AudioDeviceModel>) {
        val modalBottomSheet = AudioDevicesMenuDialogFragment(devicesList)
        modalBottomSheet.show(parentFragmentManager, AudioDevicesMenuDialogFragment.TAG)
        bottomSheetDialog = modalBottomSheet
    }
}
