package com.lqr.paperragserver.conversation.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lqr.paperragserver.conversation.entity.Conversation;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.util.UUID;

public interface ConversationMapper extends BaseMapper<Conversation> {

    @Update("""
            update public.conversation
            set title = #{title}, updated_at = now()
            where id = #{conversationId}
              and owner_user_id = #{ownerUserId}
              and deleted_at is null
            """)
    int updateTitle(@Param("conversationId") UUID conversationId,
                    @Param("ownerUserId") UUID ownerUserId,
                    @Param("title") String title);

    @Update("""
            update public.conversation
            set updated_at = now()
            where id = #{conversationId}
              and owner_user_id = #{ownerUserId}
              and deleted_at is null
            """)
    int touch(@Param("conversationId") UUID conversationId, @Param("ownerUserId") UUID ownerUserId);

    @Update("""
            update public.conversation
            set deleted_at = now(), updated_at = now()
            where id = #{conversationId}
              and owner_user_id = #{ownerUserId}
              and deleted_at is null
            """)
    int softDelete(@Param("conversationId") UUID conversationId, @Param("ownerUserId") UUID ownerUserId);
}