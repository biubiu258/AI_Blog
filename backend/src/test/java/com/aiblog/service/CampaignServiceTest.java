package com.aiblog.service;

import com.aiblog.common.BusinessException;
import com.aiblog.entity.Campaign;
import com.aiblog.entity.User;
import com.aiblog.mapper.CampaignMapper;
import com.aiblog.mapper.UserMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 创作活动纯逻辑单测：字段校验与上线通知。
 */
class CampaignServiceTest {

    private final CampaignMapper campaignMapper = mock(CampaignMapper.class);
    private final UserMapper userMapper = mock(UserMapper.class);
    private final NotificationService notificationService = mock(NotificationService.class);
    private final CampaignService service = new CampaignService(
            campaignMapper, userMapper, notificationService);

    private CampaignService.CampaignRequest request(String title,
                                                    LocalDateTime start, LocalDateTime end) {
        return new CampaignService.CampaignRequest(title, "说明", "奖池 1000 元",
                "发布带话题文章", "编辑评选", start, end, null);
    }

    @Test
    void createRejectsBlankTitleAndMissingTimes() {
        LocalDateTime now = LocalDateTime.now();
        assertThrows(BusinessException.class,
                () -> service.create(request(" ", now, now.plusDays(7))));
        assertThrows(BusinessException.class, () -> service.create(request("征文", null, now)));
        assertThrows(BusinessException.class,
                () -> service.create(request("征文", now.plusDays(7), now)));
    }

    @Test
    void createDefaultsToDraft() {
        LocalDateTime now = LocalDateTime.now();
        Map<String, Object> result = service.create(request("秋日征文", now, now.plusDays(7)));
        assertEquals("draft", result.get("status"));
        assertEquals("秋日征文", result.get("title"));
    }

    @Test
    void publishNotifiesActiveUsers() {
        Campaign campaign = new Campaign();
        campaign.setId(3L);
        campaign.setTitle("秋日征文");
        campaign.setPrizePool("奖池 1000 元");
        campaign.setJoinRule("发布带话题文章");
        when(campaignMapper.selectById(3L)).thenReturn(campaign);
        User first = new User();
        first.setId(10L);
        User second = new User();
        second.setId(11L);
        when(userMapper.selectList(any())).thenReturn(List.of(first, second));

        service.publish(3L);

        verify(campaignMapper).updateById(campaign);
        verify(notificationService).notify(eq(10L), eq("system"), isNull(), isNull(), isNull(),
                anyString());
        verify(notificationService).notify(eq(11L), eq("system"), isNull(), isNull(), isNull(),
                anyString());
    }

    @Test
    void updateRejectsUnknownStatus() {
        Campaign campaign = new Campaign();
        campaign.setId(3L);
        when(campaignMapper.selectById(3L)).thenReturn(campaign);
        LocalDateTime now = LocalDateTime.now();
        assertThrows(BusinessException.class, () -> service.update(3L,
                new CampaignService.CampaignRequest("征文", null, null, null, null,
                        now, now.plusDays(3), "closed")));
    }
}
