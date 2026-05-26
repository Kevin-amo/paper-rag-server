package com.lqr.paperragserver.conversation.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lqr.paperragserver.conversation.entity.ConversationMessage;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.UUID;

/**
 * 会话消息数据访问接口，提供消息顺序分配和最近消息窗口查询。
 */
public interface ConversationMessageMapper extends BaseMapper<ConversationMessage> {

    @Select("""
            select coalesce(max(message_order), 0) + 1
            from public.conversation_message
            where conversation_id = #{conversationId}
              and owner_user_id = #{ownerUserId}
            """)
    int nextMessageOrder(@Param("conversationId") UUID conversationId, @Param("ownerUserId") UUID ownerUserId);

    @Select("""
            select *
            from (
                select *
                from public.conversation_message
                where conversation_id = #{conversationId}
                  and owner_user_id = #{ownerUserId}
                order by message_order desc
                limit #{limit}
            ) recent_messages
            order by message_order asc
            """)
    List<ConversationMessage> selectRecent(@Param("conversationId") UUID conversationId,
                                           @Param("ownerUserId") UUID ownerUserId,
                                           @Param("limit") int limit);
}