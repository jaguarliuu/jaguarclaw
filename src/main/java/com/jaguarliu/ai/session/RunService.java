package com.jaguarliu.ai.session;

import com.jaguarliu.ai.storage.entity.RunEntity;
import com.jaguarliu.ai.storage.repository.RunRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Run 服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RunService {

    public static final String DEFAULT_PRINCIPAL_ID = "local-default";
    public static final String DEFAULT_AGENT_ID = "main";

    private final RunRepository runRepository;

    /**
     * 创建新 Run（主运行）
     */
    @Transactional
    public RunEntity create(String sessionId, String prompt) {
        return create(sessionId, prompt, DEFAULT_AGENT_ID, DEFAULT_PRINCIPAL_ID);
    }

    /**
     * 创建新 Run（指定 agentId）
     */
    @Transactional
    public RunEntity create(String sessionId, String prompt, String agentId) {
        return create(sessionId, prompt, agentId, DEFAULT_PRINCIPAL_ID);
    }

    /**
     * 创建新 Run（指定 agentId + ownerPrincipalId）
     */
    @Transactional
    public RunEntity create(String sessionId, String prompt, String agentId, String ownerPrincipalId) {
        String resolvedAgentId = normalizeAgentId(agentId);
        RunEntity run = RunEntity.builder()
                .id(UUID.randomUUID().toString())
                .sessionId(sessionId)
                .status(RunStatus.QUEUED.getValue())
                .prompt(prompt)
                .agentId(resolvedAgentId)
                .runKind("main")
                .lane("main")
                .deliver(false)
                .ownerPrincipalId(ownerPrincipalId)
                .build();

        run = runRepository.save(run);
        log.info("Created run: id={}, sessionId={}, agentId={}, status={}",
                run.getId(), sessionId, resolvedAgentId, run.getStatus());
        return run;
    }

    /**
     * 创建子代理运行
     *
     * @param sessionId          子会话 ID
     * @param parentRunId        父运行 ID
     * @param requesterSessionId 请求方会话 ID（父会话）
     * @param agentId            Agent Profile ID
     * @param prompt             任务提示
     * @param deliver            是否转发中间流
     * @return 新创建的子代理运行
     */
    @Transactional
    public RunEntity createSubagentRun(String sessionId,
                                        String parentRunId,
                                        String requesterSessionId,
                                        String agentId,
                                        String prompt,
                                        boolean deliver) {
        return createSubagentRun(sessionId, parentRunId, requesterSessionId, agentId, prompt, deliver, DEFAULT_PRINCIPAL_ID);
    }

    /**
     * 创建子代理运行（指定 ownerPrincipalId）
     */
    @Transactional
    public RunEntity createSubagentRun(String sessionId,
                                        String parentRunId,
                                        String requesterSessionId,
                                        String agentId,
                                        String prompt,
                                        boolean deliver,
                                        String ownerPrincipalId) {
        RunEntity run = RunEntity.builder()
                .id(UUID.randomUUID().toString())
                .sessionId(sessionId)
                .status(RunStatus.QUEUED.getValue())
                .prompt(prompt)
                .agentId(agentId)
                .runKind("subagent")
                .lane("subagent")
                .parentRunId(parentRunId)
                .requesterSessionId(requesterSessionId)
                .deliver(deliver)
                .ownerPrincipalId(ownerPrincipalId)
                .build();

        run = runRepository.save(run);
        log.info("Created subagent run: id={}, sessionId={}, parentRunId={}, requesterSessionId={}, agentId={}, deliver={}",
                run.getId(), sessionId, parentRunId, requesterSessionId, agentId, deliver);
        return run;
    }

    /**
     * 更新 Run 状态
     */
    @Transactional
    public RunEntity updateStatus(String runId, RunStatus newStatus) {
        RunEntity run = runRepository.findById(runId)
                .orElseThrow(() -> new IllegalArgumentException("Run not found: " + runId));

        RunStatus currentStatus = RunStatus.fromValue(run.getStatus());

        if (!currentStatus.canTransitionTo(newStatus)) {
            throw new IllegalStateException(
                    String.format("Invalid status transition: %s -> %s", currentStatus, newStatus));
        }

        run.setStatus(newStatus.getValue());
        run = runRepository.save(run);
        log.info("Updated run status: id={}, {} -> {}", runId, currentStatus, newStatus);
        return run;
    }

    /**
     * 获取 Run
     */
    public Optional<RunEntity> get(String runId) {
        return runRepository.findById(runId);
    }

    /**
     * 获取指定主体 Run
     */
    public Optional<RunEntity> get(String runId, String ownerPrincipalId) {
        return runRepository.findByIdAndOwnerPrincipalId(runId, ownerPrincipalId);
    }

    /**
     * 获取 Session 下的所有 Run
     */
    public List<RunEntity> listBySession(String sessionId) {
        return runRepository.findBySessionIdOrderByCreatedAtDesc(sessionId);
    }

    /**
     * 获取主体下 Session 的所有 Run
     */
    public List<RunEntity> listBySession(String sessionId, String ownerPrincipalId) {
        return runRepository.findBySessionIdAndOwnerPrincipalIdOrderByCreatedAtDesc(sessionId, ownerPrincipalId);
    }

    /**
     * 获取指定父运行的所有子代理运行
     */
    public List<RunEntity> listSubagentRuns(String parentRunId) {
        return runRepository.findByParentRunIdOrderByCreatedAtDesc(parentRunId);
    }

    /**
     * 获取指定 lane 的运行中的 Run 数量
     */
    public long countRunningByLane(String lane) {
        return runRepository.countByLaneAndStatus(lane, RunStatus.RUNNING.getValue());
    }

    /**
     * 获取指定 lane 的排队中的 Run
     */
    public List<RunEntity> listQueuedByLane(String lane) {
        return runRepository.findByLaneAndStatusOrderByCreatedAtAsc(lane, RunStatus.QUEUED.getValue());
    }

    /**
     * 获取指定请求会话的所有子代理运行
     */
    public List<RunEntity> listByRequesterSession(String requesterSessionId) {
        return runRepository.findByRequesterSessionIdOrderByCreatedAtDesc(requesterSessionId);
    }

    private String normalizeAgentId(String agentId) {
        if (agentId == null || agentId.isBlank()) {
            return DEFAULT_AGENT_ID;
        }
        return agentId;
    }
}
