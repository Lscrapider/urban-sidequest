package com.urbansidequest.backend.provider.route;

import com.urbansidequest.backend.config.PoiSearchProviderProperties;
import com.urbansidequest.backend.config.PoiSearchProviderProperties.Provider;
import com.urbansidequest.backend.domain.dto.PoiCandidateDTO;
import com.urbansidequest.backend.handler.route.context.RouteGenerationContext;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Primary
@Component
public class ConfigurablePoiCandidateProvider implements PoiCandidateProvider {

    private static final Logger LOGGER = LoggerFactory.getLogger(ConfigurablePoiCandidateProvider.class);

    private final AmapPoiCandidateProvider amapPoiCandidateProvider;

    private final BaiduPoiCandidateProvider baiduPoiCandidateProvider;

    private final PoiSearchProviderProperties poiSearchProviderProperties;

    public ConfigurablePoiCandidateProvider(
            AmapPoiCandidateProvider amapPoiCandidateProvider,
            BaiduPoiCandidateProvider baiduPoiCandidateProvider,
            PoiSearchProviderProperties poiSearchProviderProperties
    ) {
        this.amapPoiCandidateProvider = amapPoiCandidateProvider;
        this.baiduPoiCandidateProvider = baiduPoiCandidateProvider;
        this.poiSearchProviderProperties = poiSearchProviderProperties;
    }

    @Override
    public List<PoiCandidateDTO> loadCandidates(RouteGenerationContext context) {
        Provider provider = this.poiSearchProviderProperties.getProvider();
        return switch (provider) {
            case AMAP -> this.loadFrom(Provider.AMAP, context);
            case BAIDU -> this.loadFrom(Provider.BAIDU, context);
            case MIX -> this.loadFromMix(context);
        };
    }

    private List<PoiCandidateDTO> loadFromMix(RouteGenerationContext context) {
        Provider selectedProvider = this.selectMixProvider(context);
        Provider fallbackProvider = selectedProvider == Provider.AMAP ? Provider.BAIDU : Provider.AMAP;
        try {
            context.addWarning("训练 POI provider=mix，本批使用：" + this.displayName(selectedProvider));
            return this.loadFrom(selectedProvider, context);
        } catch (RuntimeException exception) {
            context.addWarning("训练 POI provider=mix，" + this.displayName(selectedProvider)
                    + " 查询失败，尝试切换到：" + this.displayName(fallbackProvider));
            LOGGER.warn(
                    "混合 POI provider 主数据源失败，selectedProvider={}，candidateSetId={}",
                    selectedProvider,
                    context.getCandidateSetId(),
                    exception
            );
            return this.loadFrom(fallbackProvider, context);
        }
    }

    private List<PoiCandidateDTO> loadFrom(Provider provider, RouteGenerationContext context) {
        return switch (provider) {
            case AMAP -> this.amapPoiCandidateProvider.loadCandidates(context);
            case BAIDU -> this.baiduPoiCandidateProvider.loadCandidates(context);
            case MIX -> this.loadFromMix(context);
        };
    }

    private Provider selectMixProvider(RouteGenerationContext context) {
        int totalWeight = this.poiSearchProviderProperties.getMix().totalWeight();
        int amapWeight = this.poiSearchProviderProperties.getMix().effectiveAmapWeight();
        int bucket = Math.floorMod(context.getCandidateSetId().hashCode(), totalWeight);
        return bucket < amapWeight ? Provider.AMAP : Provider.BAIDU;
    }

    private String displayName(Provider provider) {
        return switch (provider) {
            case AMAP -> "高德";
            case BAIDU -> "百度";
            case MIX -> "混合";
        };
    }
}
