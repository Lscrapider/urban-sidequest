package com.urbansidequest.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "poi.search")
public class PoiSearchProviderProperties {

    private static final int DEFAULT_AMAP_WEIGHT = 1;

    private static final int DEFAULT_BAIDU_WEIGHT = 2;

    private Provider provider = Provider.AMAP;

    private Mix mix = new Mix();

    public Provider getProvider() {
        return this.provider;
    }

    public void setProvider(Provider provider) {
        this.provider = provider == null ? Provider.AMAP : provider;
    }

    public Mix getMix() {
        return this.mix;
    }

    public void setMix(Mix mix) {
        this.mix = mix == null ? new Mix() : mix;
    }

    public enum Provider {
        AMAP,
        BAIDU,
        MIX
    }

    public static class Mix {

        private int amapWeight = DEFAULT_AMAP_WEIGHT;

        private int baiduWeight = DEFAULT_BAIDU_WEIGHT;

        public int getAmapWeight() {
            return this.amapWeight;
        }

        public void setAmapWeight(int amapWeight) {
            this.amapWeight = Math.max(0, amapWeight);
        }

        public int getBaiduWeight() {
            return this.baiduWeight;
        }

        public void setBaiduWeight(int baiduWeight) {
            this.baiduWeight = Math.max(0, baiduWeight);
        }

        public int totalWeight() {
            int total = this.amapWeight + this.baiduWeight;
            return total > 0 ? total : DEFAULT_AMAP_WEIGHT + DEFAULT_BAIDU_WEIGHT;
        }

        public int effectiveAmapWeight() {
            return this.amapWeight + this.baiduWeight > 0 ? this.amapWeight : DEFAULT_AMAP_WEIGHT;
        }
    }
}
