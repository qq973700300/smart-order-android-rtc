package com.example.test5.order;

import android.content.Context;
import android.widget.Toast;

import com.example.test5.R;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/** 送厨结果提示：成功弹窗展示明细，失败 Toast。 */
public final class OrderSubmitDialogs {

    private OrderSubmitDialogs() {
    }

    public static void show(Context context, boolean success, String message, String submittedSummary) {
        if (success) {
            String body = submittedSummary == null || submittedSummary.isEmpty()
                    ? message
                    : context.getString(R.string.order_submit_success_body, submittedSummary);
            new MaterialAlertDialogBuilder(context)
                    .setTitle(R.string.order_submit_success_title)
                    .setMessage(body)
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
        } else {
            Toast.makeText(context,
                    message == null || message.isEmpty()
                            ? context.getString(R.string.order_submit_failed)
                            : message,
                    Toast.LENGTH_LONG).show();
        }
    }
}
