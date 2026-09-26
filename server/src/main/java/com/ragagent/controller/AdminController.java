package com.ragagent.controller;

import com.ragagent.model.Message;
import com.ragagent.model.Order;
import com.ragagent.model.UserBehavior;
import com.ragagent.service.AdminService;
import com.ragagent.service.AdminService.ConversationRow;
import com.ragagent.service.AdminService.LogEventRow;
import com.ragagent.service.AdminService.Overview;
import com.ragagent.service.AdminService.PageResult;
import com.ragagent.service.AdminService.TimeseriesPoint;
import com.ragagent.service.AdminService.UserRow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 管理后台只读接口。整个 /api/admin/** 由 AdminAuthInterceptor 要求 ADMIN 角色。
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private static final Logger log = LoggerFactory.getLogger(AdminController.class);
    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    /** 概览：各类计数 + GMV + 今日增量 */
    @GetMapping("/overview")
    public Overview overview() {
        return adminService.overview();
    }

    /** 用户列表（分页 + 用户名搜索） */
    @GetMapping("/users")
    public PageResult<UserRow> users(@RequestParam(defaultValue = "1") int page,
                                     @RequestParam(defaultValue = "20") int size,
                                     @RequestParam(required = false) String q) {
        return adminService.users(page, size, q);
    }

    /** 会话列表（分页 + 用户/关键词过滤） */
    @GetMapping("/conversations")
    public PageResult<ConversationRow> conversations(@RequestParam(defaultValue = "1") int page,
                                                     @RequestParam(defaultValue = "20") int size,
                                                     @RequestParam(required = false) Long userId,
                                                     @RequestParam(required = false) String q) {
        return adminService.conversations(page, size, userId, q);
    }

    /** 某会话的全部消息 */
    @GetMapping("/conversations/{conversationId}/messages")
    public List<Message> messages(@PathVariable String conversationId) {
        return adminService.messages(conversationId);
    }

    /** 订单列表（分页） */
    @GetMapping("/orders")
    public PageResult<Order> orders(@RequestParam(defaultValue = "1") int page,
                                    @RequestParam(defaultValue = "20") int size) {
        return adminService.orders(page, size);
    }

    /** 用户行为列表（分页 + 行为类型过滤） */
    @GetMapping("/behaviors")
    public PageResult<UserBehavior> behaviors(@RequestParam(defaultValue = "1") int page,
                                              @RequestParam(defaultValue = "20") int size,
                                              @RequestParam(required = false) String actionType) {
        return adminService.behaviors(page, size, actionType);
    }

    /** 活跃量时间序列（近 N 天：新增用户 / 新增会话 / 新增订单） */
    @GetMapping("/metrics/timeseries")
    public List<TimeseriesPoint> timeseries(@RequestParam(defaultValue = "30") int days) {
        return adminService.timeseries(days);
    }

    /** 日志事件（ERROR + 关键动作，分页 + 分类/级别/来源/关键词筛选） */
    @GetMapping("/logs")
    public PageResult<LogEventRow> logs(@RequestParam(defaultValue = "1") int page,
                                        @RequestParam(defaultValue = "50") int size,
                                        @RequestParam(required = false) String category,
                                        @RequestParam(required = false) String level,
                                        @RequestParam(required = false) String logger,
                                        @RequestParam(required = false) String q) {
        return adminService.logs(page, size, category, level, logger, q);
    }

    /** 评测运行列表（分页，按时间倒序） */
    @GetMapping("/judge/runs")
    public PageResult<com.ragagent.repository.JudgeRunRepository.RunRow> judgeRuns(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return adminService.judgeRuns(page, size);
    }

    /** 某次评测的用例明细 */
    @GetMapping("/judge/runs/{runId}/cases")
    public List<com.ragagent.repository.JudgeRunRepository.CaseRow> judgeCases(@PathVariable long runId) {
        return adminService.judgeCases(runId);
    }
}
