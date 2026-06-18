package org.ruoyi.mapper.usercenter;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.ruoyi.common.mybatis.core.mapper.BaseMapperPlus;
import org.ruoyi.domain.entity.usercenter.UcWorkFile;
import org.ruoyi.domain.vo.usercenter.UcWorkFileVo;

import java.util.Date;

@Mapper
public interface UcWorkFileMapper extends BaseMapperPlus<UcWorkFile, UcWorkFileVo> {

    /**
     * 物理删除超过截止时间的作品，释放 LONGTEXT 存储空间
     */
    @InterceptorIgnore(tenantLine = "true", dataPermission = "true")
    @Delete("""
        DELETE FROM uc_work_file
        WHERE del_flag = '0'
          AND COALESCE(update_time, create_time) < #{cutoff}
        """)
    int physicalDeleteExpired(@Param("cutoff") Date cutoff);
}
