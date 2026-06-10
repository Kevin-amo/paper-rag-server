package com.lqr.papermind.conversation.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lqr.papermind.conversation.entity.Conversation;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.util.UUID;

/**
 * 会话数据访问接口，提供会话标题更新、活跃时间刷新和软删除操作。
 */
public interface ConversationMapper extends BaseMapper<Conversation> {

    /**
     * 更新指定用户会话的标题，并刷新会话更新时间。
     *
     * @param conversationId 会话 ID
     * @param ownerUserId 会话所属用户 ID
     * @param title 新标题
     * @return 受影响的记录数
     */
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

    /**
     * 刷新指定用户会话的活跃时间。
     *
     * @param conversationId 会话 ID
     * @param ownerUserId 会话所属用户 ID
     * @return 受影响的记录数
     */
    @Update("""
            update public.conversation
            set updated_at = now()
            where id = #{conversationId}
              and owner_user_id = #{ownerUserId}
              and deleted_at is null
            """)
    int touch(@Param("conversationId") UUID conversationId, @Param("ownerUserId") UUID ownerUserId);

    /**
     * 软删除指定用户会话，并同步刷新更新时间。
     *
     * @param conversationId 会话 ID
     * @param ownerUserId 会话所属用户 ID
     * @return 受影响的记录数
     */
    @Update("""
            update public.conversation
            set deleted_at = now(), updated_at = now()
            where id = #{conversationId}
              and owner_user_id = #{ownerUserId}
              and deleted_at is null
            """)
    int softDelete(@Param("conversationId") UUID conversationId, @Param("ownerUserId") UUID ownerUserId);
}