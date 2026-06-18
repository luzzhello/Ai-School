package org.ruoyi.domain.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class SystemArchitectureResponse {

    private String title;

    private String archType;

    private List<SystemArchitectureLayerVo> layers;

    private List<SystemArchitectureConnectionVo> connections;
}
