package com.jaguarliu.ai.runtime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReactEntrySkillSelection {

    private String skillName;

    private String reason;

    private Double confidence;

    public boolean hasSelectedSkill() {
        return skillName != null && !skillName.isBlank() && !"NONE".equalsIgnoreCase(skillName);
    }
}
