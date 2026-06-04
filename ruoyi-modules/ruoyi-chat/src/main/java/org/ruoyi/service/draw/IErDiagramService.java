package org.ruoyi.service.draw;

import org.ruoyi.domain.dto.request.ErDiagramGenerateRequest;
import org.ruoyi.domain.dto.request.ErSqlOptimizeRequest;
import org.ruoyi.domain.dto.response.ErDiagramResponse;
import org.ruoyi.domain.dto.response.ErSqlOptimizeResponse;
import org.ruoyi.domain.dto.response.ErSqlTestResponse;

/**
 * ER 图生成服务
 */
public interface IErDiagramService {

    ErDiagramResponse generate(ErDiagramGenerateRequest request);

    ErSqlTestResponse testSql(String sql);

    ErSqlOptimizeResponse optimizeSql(ErSqlOptimizeRequest request);
}
