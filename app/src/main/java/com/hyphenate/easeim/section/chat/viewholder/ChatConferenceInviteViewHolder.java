package com.hyphenate.easeim.section.chat.viewholder;

import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;

import com.hyphenate.chat.EMMessage;
import com.hyphenate.easeim.common.constant.DemoConstant;
import com.hyphenate.easeim.common.manager.PushAndMessageHelper;
import com.hyphenate.easeim.section.chat.views.ChatRowConferenceInvite;
import com.hyphenate.easeui.interfaces.MessageListItemClickListener;
import com.hyphenate.easeui.model.styles.EaseMessageListItemStyle;
import com.hyphenate.easeui.viewholder.EaseChatRowViewHolder;

public class ChatConferenceInviteViewHolder extends EaseChatRowViewHolder {

    public ChatConferenceInviteViewHolder(@NonNull View itemView, MessageListItemClickListener itemClickListener, EaseMessageListItemStyle itemStyle) {
        super(itemView, itemClickListener, itemStyle);
    }

    public static ChatConferenceInviteViewHolder create(ViewGroup parent, boolean isSender,
                                                        MessageListItemClickListener itemClickListener, EaseMessageListItemStyle itemStyle) {
        return new ChatConferenceInviteViewHolder(new ChatRowConferenceInvite(parent.getContext(), isSender), itemClickListener, itemStyle);
    }

    @Override
    public void onBubbleClick(EMMessage message) {
        super.onBubbleClick(message);
        String confId = message.getStringAttribute(DemoConstant.MSG_ATTR_CONF_ID, "");
        String confPassword = message.getStringAttribute(DemoConstant.MSG_ATTR_CONF_PASS,"");
        String extension = message.getStringAttribute(DemoConstant.MSG_ATTR_EXTENSION, "");
        PushAndMessageHelper.goConference(getContext(), confId, confPassword, extension);
    }
}
