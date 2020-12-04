package com.hyphenate.easeim.section.message.delegates;

import android.content.Context;
import android.view.View;
import android.widget.TextView;

import com.hyphenate.easeim.DemoHelper;
import com.hyphenate.easeim.R;
import com.hyphenate.easeim.common.db.entity.InviteMessage;
import com.hyphenate.easeim.common.manager.PushAndMessageHelper;
import com.hyphenate.easeui.adapter.EaseBaseDelegate;
import com.hyphenate.easeui.adapter.EaseBaseRecyclerViewAdapter;
import com.hyphenate.easeim.common.db.entity.InviteMessage.InviteMessageStatus;
import com.hyphenate.easeui.widget.EaseImageView;
import com.hyphenate.util.DateUtils;

import java.util.Date;

import androidx.annotation.NonNull;

public class OtherMsgDelegate extends EaseBaseDelegate<InviteMessage, OtherMsgDelegate.ViewHolder> {

    @Override
    public boolean isForViewType(InviteMessage msg, int position) {
        return msg.getStatusEnum() != InviteMessageStatus.BEINVITEED &&
                msg.getStatusEnum() != InviteMessageStatus.BEAPPLYED &&
                msg.getStatusEnum() != InviteMessageStatus.GROUPINVITATION &&
                msg.getStatusEnum() != InviteMessageStatus.BEAGREED;
    }

    @Override
    protected int getLayoutId() {
        return R.layout.demo_layout_item_invite_msg_agree;
    }

    @Override
    protected OtherMsgDelegate.ViewHolder createViewHolder(View view) {
        return new ViewHolder(view);
    }

    public class ViewHolder extends EaseBaseRecyclerViewAdapter.ViewHolder<InviteMessage> {
        private TextView name;
        private TextView message;
        private EaseImageView avatar;
        private TextView time;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
        }

        @Override
        public void initView(View itemView) {
            name = findViewById(R.id.name);
            message = findViewById(R.id.message);
            avatar = findViewById(R.id.avatar);
            time = findViewById(R.id.time);
            avatar.setShapeType(DemoHelper.getInstance().getEaseAvatarOptions().getAvatarShape());
        }

        @Override
        public void setData(InviteMessage msg, int position) {
            name.setText(msg.getFrom());
            time.setText(DateUtils.getTimestampString(new Date(msg.getTime())));
            String str = PushAndMessageHelper.getSystemMessage(msg);
            message.setText(str);
        }
    }
}
