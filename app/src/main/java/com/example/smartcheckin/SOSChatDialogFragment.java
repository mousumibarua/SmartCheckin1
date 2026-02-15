package com.example.smartcheckin;

import android.os.Bundle;
import android.view.*;
import android.widget.*;

import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;

public class SOSChatDialogFragment extends DialogFragment {

    private EditText edtMessage;
    private LinearLayout chatContainer;
    private ScrollView chatScroll;

    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            ViewGroup container,
            Bundle savedInstanceState
    ) {
        View view = inflater.inflate(
                R.layout.dialog_sos_chat,
                container,
                false
        );

        edtMessage = view.findViewById(R.id.edtMessage);
        chatContainer = view.findViewById(R.id.chatContainer);
        chatScroll = view.findViewById(R.id.chatScroll);

        Button btnSend = view.findViewById(R.id.btnSendSOS);
        Button btnCancel = view.findViewById(R.id.btnCancel);

        // 🤖 Initial bot message
        addBotMessage(
                "What is the emergency?\n\n" +
                        "1️⃣ I feel unsafe\n" +
                        "2️⃣ Medical emergency\n" +
                        "3️⃣ Running late / delayed\n" +
                        "4️⃣ Other (type message)"
        );

        btnSend.setOnClickListener(v -> handleSend());
        btnCancel.setOnClickListener(v -> dismiss());

        return view;
    }

    /* ---------- POSITION BOTTOM RIGHT ---------- */
    @Override
    public void onStart() {
        super.onStart();

        if (getDialog() != null && getDialog().getWindow() != null) {
            Window window = getDialog().getWindow();
            WindowManager.LayoutParams params = window.getAttributes();

            params.gravity = Gravity.BOTTOM | Gravity.END;
            params.x = 24;
            params.y = 48;

            window.setAttributes(params);
            window.setLayout(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            window.setBackgroundDrawableResource(android.R.color.transparent);
        }
    }

    /* ---------- SEND HANDLER ---------- */
    private void handleSend() {

        String input = edtMessage.getText().toString().trim();
        String reason;

        switch (input) {
            case "1":
                reason = "User feels unsafe";
                break;
            case "2":
                reason = "Medical emergency";
                break;
            case "3":
                reason = "User is running late";
                break;
            default:
                reason = input.isEmpty()
                        ? "Emergency assistance needed"
                        : input;
        }

        addUserMessage(reason);
        addBotMessage("Emergency recorded. Contacting help now.");

        // ✅ ONLY DELEGATE — NOTHING ELSE
        if (isAdded() && getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).onSendSosClicked(reason);
        }

        edtMessage.setText("");
        dismiss();
    }

    /* ---------- CHAT UI ---------- */

    private void addBotMessage(String msg) {
        if (getContext() == null) return;

        TextView tv = new TextView(getContext());
        tv.setText("🤖 " + msg);
        tv.setPadding(20, 12, 20, 12);
        tv.setBackgroundResource(R.drawable.bg_bot);
        chatContainer.addView(tv);
        scroll();
    }

    private void addUserMessage(String msg) {
        if (getContext() == null) return;

        TextView tv = new TextView(getContext());
        tv.setText("👤 " + msg);
        tv.setPadding(20, 12, 20, 12);
        tv.setBackgroundResource(R.drawable.bg_user);
        chatContainer.addView(tv);
        scroll();
    }

    private void scroll() {
        if (chatScroll != null) {
            chatScroll.post(() ->
                    chatScroll.fullScroll(View.FOCUS_DOWN)
            );
        }
    }
}
