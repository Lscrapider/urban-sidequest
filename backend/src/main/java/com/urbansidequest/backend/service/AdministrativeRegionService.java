package com.urbansidequest.backend.service;

import com.urbansidequest.backend.domain.vo.AdministrativeRegionVO;
import java.util.List;

public interface AdministrativeRegionService {

    List<AdministrativeRegionVO> listRegions(String parentAdcode);
}
