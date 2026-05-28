package com.example.trustlock.ui.approval;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.example.trustlock.databinding.DialogWaitingApprovalBinding;
import com.example.trustlock.models.ApprovalRequest;
import com.example.trustlock.util.ApprovalRequestManager;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class WaitingForApprovalDialog extends DialogFragment {

    public interface OnApprovalResultListener {
        void onApproved();
        void onDenied();
    }

    private static final String ARG_REQUEST_ID    = "requestId";
    private static final String ARG_GUARDIAN_EMAIL = "guardianEmail";
    private static final String ARG_DESCRIPTION   = "description";

    private DialogWaitingApprovalBinding binding;
    private ApprovalRequestManager approvalManager;
    private OnApprovalResultListener resultListener;

    public static WaitingForApprovalDialog newInstance(
            String requestId, String guardianEmail, String description) {
        WaitingForApprovalDialog dialog = new WaitingForApprovalDialog();
        Bundle args = new Bundle();
        args.putString(ARG_REQUEST_ID,    requestId);
        args.putString(ARG_GUARDIAN_EMAIL, guardianEmail);
        args.putString(ARG_DESCRIPTION,   description);
        dialog.setArguments(args);
        return dialog;
    }

    public void setOnApprovalResultListener(OnApprovalResultListener listener) {
        this.resultListener = listener;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        binding = DialogWaitingApprovalBinding.inflate(LayoutInflater.from(requireContext()));
        approvalManager = new ApprovalRequestManager();

        Bundle args = requireArguments();
        String guardianEmail = args.getString(ARG_GUARDIAN_EMAIL, "your guardian");
        String description   = args.getString(ARG_DESCRIPTION, "");

        binding.tvWaitingText.setText("Waiting for " + guardianEmail + " to approve...");
        binding.tvDescription.setText(description);

        return new MaterialAlertDialogBuilder(requireContext())
                .setView(binding.getRoot())
                .setCancelable(false)
                .setNegativeButton("Cancel", (d, w) -> {
                    String requestId = args.getString(ARG_REQUEST_ID);
                    if (requestId != null) approvalManager.cancelRequest(requestId);
                })
                .create();
    }

    @Override
    public void onStart() {
        super.onStart();
        String requestId = requireArguments().getString(ARG_REQUEST_ID);
        if (requestId == null) return;

        approvalManager.listenForApproval(requestId, status -> {
            if (!isAdded()) return;
            requireActivity().runOnUiThread(() -> {
                dismissAllowingStateLoss();
                if (resultListener == null) return;
                if (ApprovalRequest.STATUS_APPROVED.equals(status)) {
                    resultListener.onApproved();
                } else {
                    resultListener.onDenied();
                }
            });
        });
    }

    @Override
    public void onStop() {
        super.onStop();
        approvalManager.stopListening();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
