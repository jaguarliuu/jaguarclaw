package com.jaguarliu.ai.runtime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class ExecutionPlan {
    private String goal;
    private ExecutionPlanStatus status;
    private String currentItemId;
    @Builder.Default
    private List<PlanItem> items = new ArrayList<>();
    private int revision;

    public Optional<PlanItem> currentItem() {
        if (currentItemId == null || items == null) {
            return Optional.empty();
        }
        return items.stream().filter(item -> currentItemId.equals(item.getId())).findFirst();
    }

    public boolean allItemsDone() {
        return items != null && !items.isEmpty() && items.stream().allMatch(item -> item.getStatus() == PlanItemStatus.DONE);
    }

    public long blockedItemsCount() {
        return items == null ? 0 : items.stream().filter(item -> item.getStatus() == PlanItemStatus.BLOCKED).count();
    }

    public long remainingItemsCount() {
        return items == null ? 0 : items.stream().filter(item -> item.getStatus() != PlanItemStatus.DONE && item.getStatus() != PlanItemStatus.CANCELLED).count();
    }
}
