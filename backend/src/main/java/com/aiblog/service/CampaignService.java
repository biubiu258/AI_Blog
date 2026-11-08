package com.aiblog.service;

import com.aiblog.common.BusinessException;
import com.aiblog.common.ErrorCode;
import com.aiblog.entity.Campaign;
import com.aiblog.entity.User;
import com.aiblog.mapper.CampaignMapper;
import com.aiblog.mapper.UserMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 创作激励活动：后台配置时间、奖池、参与条件与评选方式，发布时站内通知全站作者。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CampaignService {

    public static final String STATUS_DRAFT = "draft";
    public static final String STATUS_ACTIVE = "active";
    public static final String STATUS_ENDED = "ended";

    private final CampaignMapper campaignMapper;
    private final UserMapper userMapper;
    private final NotificationService notificationService;

    public Map<String, Object> create(CampaignRequest request) {
        Campaign campaign = new Campaign();
        apply(campaign, request);
        campaign.setStatus(STATUS_DRAFT);
        campaignMapper.insert(campaign);
        return toItem(campaign);
    }

    public Map<String, Object> update(Long id, CampaignRequest request) {
        Campaign campaign = mustGet(id);
        apply(campaign, request);
        if (StringUtils.hasText(request.status())) {
            String status = request.status();
            if (!STATUS_DRAFT.equals(status) && !STATUS_ACTIVE.equals(status)
                    && !STATUS_ENDED.equals(status)) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "status 仅支持 draft / active / ended");
            }
            campaign.setStatus(status);
        }
        campaignMapper.updateById(campaign);
        return toItem(campaign);
    }

    public void delete(Long id) {
        mustGet(id);
        campaignMapper.deleteById(id);
    }

    /** 管理端列表，含草稿 */
    public Map<String, Object> adminPage(int page, int size, String status) {
        Page<Campaign> result = campaignMapper.selectPage(Page.of(page, Math.min(size, 50)),
                Wrappers.<Campaign>lambdaQuery()
                        .eq(StringUtils.hasText(status), Campaign::getStatus, status)
                        .orderByDesc(Campaign::getCreatedAt));
        return Map.of("total", result.getTotal(),
                "items", result.getRecords().stream().map(CampaignService::toItem).toList());
    }

    /** 读者可见的活动：进行中且当前时间在活动期内 */
    public List<Map<String, Object>> publicList() {
        LocalDateTime now = LocalDateTime.now();
        return campaignMapper.selectList(Wrappers.<Campaign>lambdaQuery()
                        .eq(Campaign::getStatus, STATUS_ACTIVE)
                        .le(Campaign::getStartAt, now)
                        .ge(Campaign::getEndAt, now)
                        .orderByAsc(Campaign::getEndAt)).stream()
                .map(CampaignService::toItem).toList();
    }

    /** 上线活动并通知全站正常用户（管理员操作） */
    public Map<String, Object> publish(Long id) {
        Campaign campaign = mustGet(id);
        campaign.setStatus(STATUS_ACTIVE);
        campaignMapper.updateById(campaign);
        String content = "创作活动「" + campaign.getTitle() + "」开始啦"
                + (StringUtils.hasText(campaign.getPrizePool())
                ? "，奖池：" + campaign.getPrizePool() : "")
                + (StringUtils.hasText(campaign.getJoinRule())
                ? "；参与条件：" + campaign.getJoinRule() : "");
        List<User> users = userMapper.selectList(Wrappers.<User>lambdaQuery()
                .eq(User::getStatus, 0));
        for (User user : users) {
            notificationService.notify(user.getId(), "system", null, null, null, content);
        }
        log.info("活动上线: id={}, notified={}", id, users.size());
        return toItem(campaign);
    }

    private void apply(Campaign campaign, CampaignRequest request) {
        if (!StringUtils.hasText(request.title())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "活动标题不能为空");
        }
        if (request.startAt() == null || request.endAt() == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "活动起止时间不能为空");
        }
        if (!request.startAt().isBefore(request.endAt())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "开始时间必须早于结束时间");
        }
        campaign.setTitle(request.title().trim());
        campaign.setDescription(trim(request.description(), 500));
        campaign.setPrizePool(trim(request.prizePool(), 120));
        campaign.setJoinRule(trim(request.joinRule(), 500));
        campaign.setJudgeRule(trim(request.judgeRule(), 500));
        campaign.setStartAt(request.startAt());
        campaign.setEndAt(request.endAt());
    }

    private Campaign mustGet(Long id) {
        Campaign campaign = campaignMapper.selectById(id);
        if (campaign == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "活动不存在");
        }
        return campaign;
    }

    private static String trim(String text, int max) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        String value = text.trim();
        return value.length() <= max ? value : value.substring(0, max);
    }

    private static Map<String, Object> toItem(Campaign campaign) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", campaign.getId());
        item.put("title", campaign.getTitle());
        item.put("description", campaign.getDescription());
        item.put("prizePool", campaign.getPrizePool());
        item.put("joinRule", campaign.getJoinRule());
        item.put("judgeRule", campaign.getJudgeRule());
        item.put("startAt", campaign.getStartAt());
        item.put("endAt", campaign.getEndAt());
        item.put("status", campaign.getStatus());
        item.put("createdAt", campaign.getCreatedAt());
        return item;
    }

    public record CampaignRequest(String title, String description, String prizePool,
                                  String joinRule, String judgeRule,
                                  LocalDateTime startAt, LocalDateTime endAt, String status) {
    }
}
