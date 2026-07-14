package com.urbansidequest.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.urbansidequest.backend.domain.po.AdministrativeRegionPO;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface AdministrativeRegionMapper extends BaseMapper<AdministrativeRegionPO> {

    @Select("""
            SELECT adcode, parent_adcode, name, level, longitude_gcj02, latitude_gcj02,
                   selectable, enabled, children_loaded, sort_order, created_at, updated_at
            FROM administrative_regions
            WHERE parent_adcode IS NOT DISTINCT FROM CAST(#{parentAdcode} AS VARCHAR)
              AND enabled = TRUE
            ORDER BY sort_order, name, adcode
            """)
    List<AdministrativeRegionPO> findEnabledChildren(@Param("parentAdcode") String parentAdcode);

    @Select("""
            SELECT adcode, parent_adcode, name, level, longitude_gcj02, latitude_gcj02,
                   selectable, enabled, children_loaded, sort_order, created_at, updated_at
            FROM administrative_regions
            WHERE adcode = #{adcode}
              AND enabled = TRUE
            LIMIT 1
            """)
    AdministrativeRegionPO findEnabledByAdcode(@Param("adcode") String adcode);

    @Select("""
            SELECT EXISTS (
                SELECT 1
                FROM administrative_regions
                WHERE parent_adcode = #{parentAdcode}
                  AND enabled = TRUE
            )
            """)
    boolean hasEnabledChildren(@Param("parentAdcode") String parentAdcode);

    @Insert("""
            INSERT INTO administrative_regions (
                adcode, parent_adcode, name, level, longitude_gcj02, latitude_gcj02,
                selectable, enabled, children_loaded, sort_order
            )
            VALUES (
                #{adcode}, #{parentAdcode}, #{name}, #{level}, #{longitudeGcj02}, #{latitudeGcj02},
                #{selectable}, #{enabled}, #{childrenLoaded}, #{sortOrder}
            )
            ON CONFLICT (adcode)
            DO UPDATE SET
                parent_adcode = EXCLUDED.parent_adcode,
                name = EXCLUDED.name,
                level = EXCLUDED.level,
                longitude_gcj02 = EXCLUDED.longitude_gcj02,
                latitude_gcj02 = EXCLUDED.latitude_gcj02,
                selectable = EXCLUDED.selectable,
                enabled = EXCLUDED.enabled,
                sort_order = EXCLUDED.sort_order,
                updated_at = now()
            """)
    int upsert(AdministrativeRegionPO region);

    @Update("""
            UPDATE administrative_regions
            SET children_loaded = TRUE,
                updated_at = now()
            WHERE adcode = #{adcode}
            """)
    int markChildrenLoaded(@Param("adcode") String adcode);
}
