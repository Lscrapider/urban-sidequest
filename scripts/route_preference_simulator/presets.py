from __future__ import annotations


CITY_PRESETS = {
    "shanghai": {
        "routeCityName": "上海",
        "routeCityAdcode": "310000",
        "areas": [
            {"areaLabel": "上海人民广场附近", "center": {"longitudeGcj02": 121.4737, "latitudeGcj02": 31.2304}},
            {"areaLabel": "上海衡山路-武康路附近", "center": {"longitudeGcj02": 121.4427, "latitudeGcj02": 31.2092}},
            {"areaLabel": "上海外滩-豫园附近", "center": {"longitudeGcj02": 121.4929, "latitudeGcj02": 31.2343}},
            {"areaLabel": "上海静安寺附近", "center": {"longitudeGcj02": 121.4444, "latitudeGcj02": 31.2246}},
        ],
    },
    "beijing": {
        "routeCityName": "北京",
        "routeCityAdcode": "110000",
        "areas": [
            {"areaLabel": "北京什刹海-鼓楼附近", "center": {"longitudeGcj02": 116.3974, "latitudeGcj02": 39.9442}},
            {"areaLabel": "北京前门-大栅栏附近", "center": {"longitudeGcj02": 116.3978, "latitudeGcj02": 39.8993}},
            {"areaLabel": "北京朝阳公园附近", "center": {"longitudeGcj02": 116.4825, "latitudeGcj02": 39.9440}},
        ],
    },
    "hangzhou": {
        "routeCityName": "杭州",
        "routeCityAdcode": "330100",
        "areas": [
            {"areaLabel": "杭州西湖湖滨附近", "center": {"longitudeGcj02": 120.1551, "latitudeGcj02": 30.2548}},
            {"areaLabel": "杭州武林广场附近", "center": {"longitudeGcj02": 120.1655, "latitudeGcj02": 30.2795}},
            {"areaLabel": "杭州小河直街附近", "center": {"longitudeGcj02": 120.1455, "latitudeGcj02": 30.3203}},
        ],
    },
    "chengdu": {
        "routeCityName": "成都",
        "routeCityAdcode": "510100",
        "areas": [
            {"areaLabel": "成都宽窄巷子附近", "center": {"longitudeGcj02": 104.0556, "latitudeGcj02": 30.6720}},
            {"areaLabel": "成都太古里-春熙路附近", "center": {"longitudeGcj02": 104.0807, "latitudeGcj02": 30.6530}},
            {"areaLabel": "成都玉林附近", "center": {"longitudeGcj02": 104.0603, "latitudeGcj02": 30.6264}},
        ],
    },
    "guangzhou": {
        "routeCityName": "广州",
        "routeCityAdcode": "440100",
        "areas": [
            {"areaLabel": "广州东山口附近", "center": {"longitudeGcj02": 113.2953, "latitudeGcj02": 23.1254}},
            {"areaLabel": "广州北京路附近", "center": {"longitudeGcj02": 113.2714, "latitudeGcj02": 23.1250}},
            {"areaLabel": "广州沙面附近", "center": {"longitudeGcj02": 113.2452, "latitudeGcj02": 23.1103}},
        ],
    },
    "shenzhen": {
        "routeCityName": "深圳",
        "routeCityAdcode": "440300",
        "areas": [
            {"areaLabel": "深圳华强北附近", "center": {"longitudeGcj02": 114.0859, "latitudeGcj02": 22.5446}},
            {"areaLabel": "深圳南头古城附近", "center": {"longitudeGcj02": 113.9304, "latitudeGcj02": 22.5170}},
            {"areaLabel": "深圳华侨城附近", "center": {"longitudeGcj02": 113.9866, "latitudeGcj02": 22.5425}},
        ],
    },
    "nanjing": {
        "routeCityName": "南京",
        "routeCityAdcode": "320100",
        "areas": [
            {"areaLabel": "南京新街口附近", "center": {"longitudeGcj02": 118.7848, "latitudeGcj02": 32.0422}},
            {"areaLabel": "南京夫子庙附近", "center": {"longitudeGcj02": 118.7969, "latitudeGcj02": 32.0207}},
            {"areaLabel": "南京老门东附近", "center": {"longitudeGcj02": 118.7974, "latitudeGcj02": 32.0052}},
        ],
    },
    "suzhou": {
        "routeCityName": "苏州",
        "routeCityAdcode": "320500",
        "areas": [
            {"areaLabel": "苏州平江路附近", "center": {"longitudeGcj02": 120.6317, "latitudeGcj02": 31.3138}},
            {"areaLabel": "苏州观前街附近", "center": {"longitudeGcj02": 120.6223, "latitudeGcj02": 31.3120}},
            {"areaLabel": "苏州金鸡湖附近", "center": {"longitudeGcj02": 120.7047, "latitudeGcj02": 31.3194}},
        ],
    },
    "wuhan": {
        "routeCityName": "武汉",
        "routeCityAdcode": "420100",
        "areas": [
            {"areaLabel": "武汉江汉路附近", "center": {"longitudeGcj02": 114.2947, "latitudeGcj02": 30.5794}},
            {"areaLabel": "武汉黄鹤楼附近", "center": {"longitudeGcj02": 114.3026, "latitudeGcj02": 30.5449}},
            {"areaLabel": "武汉东湖附近", "center": {"longitudeGcj02": 114.4090, "latitudeGcj02": 30.5525}},
        ],
    },
    "chongqing": {
        "routeCityName": "重庆",
        "routeCityAdcode": "500000",
        "areas": [
            {"areaLabel": "重庆解放碑附近", "center": {"longitudeGcj02": 106.5754, "latitudeGcj02": 29.5572}},
            {"areaLabel": "重庆观音桥附近", "center": {"longitudeGcj02": 106.5326, "latitudeGcj02": 29.5802}},
            {"areaLabel": "重庆磁器口附近", "center": {"longitudeGcj02": 106.4542, "latitudeGcj02": 29.5815}},
        ],
    },
    "xiamen": {
        "routeCityName": "厦门",
        "routeCityAdcode": "350200",
        "areas": [
            {"areaLabel": "厦门中山路附近", "center": {"longitudeGcj02": 118.0819, "latitudeGcj02": 24.4539}},
            {"areaLabel": "厦门沙坡尾附近", "center": {"longitudeGcj02": 118.0977, "latitudeGcj02": 24.4380}},
            {"areaLabel": "厦门鼓浪屿附近", "center": {"longitudeGcj02": 118.0676, "latitudeGcj02": 24.4442}},
        ],
    },
    "qingdao": {
        "routeCityName": "青岛",
        "routeCityAdcode": "370200",
        "areas": [
            {"areaLabel": "青岛栈桥附近", "center": {"longitudeGcj02": 120.3267, "latitudeGcj02": 36.0660}},
            {"areaLabel": "青岛八大关附近", "center": {"longitudeGcj02": 120.3548, "latitudeGcj02": 36.0552}},
            {"areaLabel": "青岛台东附近", "center": {"longitudeGcj02": 120.3616, "latitudeGcj02": 36.0887}},
        ],
    },
    "ningbo": {
        "routeCityName": "宁波",
        "routeCityAdcode": "330200",
        "areas": [
            {"areaLabel": "宁波天一广场附近", "center": {"longitudeGcj02": 121.5565, "latitudeGcj02": 29.8746}},
            {"areaLabel": "宁波老外滩附近", "center": {"longitudeGcj02": 121.5608, "latitudeGcj02": 29.8874}},
            {"areaLabel": "宁波南塘老街附近", "center": {"longitudeGcj02": 121.5448, "latitudeGcj02": 29.8547}},
        ],
    },
    "wuxi": {
        "routeCityName": "无锡",
        "routeCityAdcode": "320200",
        "areas": [
            {"areaLabel": "无锡南长街附近", "center": {"longitudeGcj02": 120.3081, "latitudeGcj02": 31.5663}},
            {"areaLabel": "无锡惠山古镇附近", "center": {"longitudeGcj02": 120.2748, "latitudeGcj02": 31.5803}},
            {"areaLabel": "无锡蠡湖附近", "center": {"longitudeGcj02": 120.2595, "latitudeGcj02": 31.5032}},
        ],
    },
    "foshan": {
        "routeCityName": "佛山",
        "routeCityAdcode": "440600",
        "areas": [
            {"areaLabel": "佛山祖庙附近", "center": {"longitudeGcj02": 113.1128, "latitudeGcj02": 23.0308}},
            {"areaLabel": "佛山岭南天地附近", "center": {"longitudeGcj02": 113.1143, "latitudeGcj02": 23.0276}},
            {"areaLabel": "佛山千灯湖附近", "center": {"longitudeGcj02": 113.1553, "latitudeGcj02": 23.0648}},
        ],
    },
    "quanzhou": {
        "routeCityName": "泉州",
        "routeCityAdcode": "350500",
        "areas": [
            {"areaLabel": "泉州西街附近", "center": {"longitudeGcj02": 118.5820, "latitudeGcj02": 24.9138}},
            {"areaLabel": "泉州开元寺附近", "center": {"longitudeGcj02": 118.5839, "latitudeGcj02": 24.9145}},
            {"areaLabel": "泉州领SHOW天地附近", "center": {"longitudeGcj02": 118.6060, "latitudeGcj02": 24.8826}},
        ],
    },
    "yangzhou": {
        "routeCityName": "扬州",
        "routeCityAdcode": "321000",
        "areas": [
            {"areaLabel": "扬州东关街附近", "center": {"longitudeGcj02": 119.4493, "latitudeGcj02": 32.3977}},
            {"areaLabel": "扬州瘦西湖附近", "center": {"longitudeGcj02": 119.4329, "latitudeGcj02": 32.4149}},
            {"areaLabel": "扬州文昌阁附近", "center": {"longitudeGcj02": 119.4358, "latitudeGcj02": 32.3941}},
        ],
    },
    "shaoxing": {
        "routeCityName": "绍兴",
        "routeCityAdcode": "330600",
        "areas": [
            {"areaLabel": "绍兴鲁迅故里附近", "center": {"longitudeGcj02": 120.5867, "latitudeGcj02": 29.9950}},
            {"areaLabel": "绍兴仓桥直街附近", "center": {"longitudeGcj02": 120.5795, "latitudeGcj02": 30.0043}},
            {"areaLabel": "绍兴书圣故里附近", "center": {"longitudeGcj02": 120.5902, "latitudeGcj02": 30.0065}},
        ],
    },
    "guilin": {
        "routeCityName": "桂林",
        "routeCityAdcode": "450300",
        "areas": [
            {"areaLabel": "桂林东西巷附近", "center": {"longitudeGcj02": 110.2994, "latitudeGcj02": 25.2810}},
            {"areaLabel": "桂林两江四湖附近", "center": {"longitudeGcj02": 110.2902, "latitudeGcj02": 25.2736}},
            {"areaLabel": "桂林象山公园附近", "center": {"longitudeGcj02": 110.2956, "latitudeGcj02": 25.2701}},
        ],
    },
    "dali": {
        "routeCityName": "大理",
        "routeCityAdcode": "532900",
        "areas": [
            {"areaLabel": "大理古城附近", "center": {"longitudeGcj02": 100.1646, "latitudeGcj02": 25.6949}},
            {"areaLabel": "大理才村码头附近", "center": {"longitudeGcj02": 100.1926, "latitudeGcj02": 25.7175}},
            {"areaLabel": "大理双廊古镇附近", "center": {"longitudeGcj02": 100.1903, "latitudeGcj02": 25.9110}},
        ],
    },
    "changsha": {
        "routeCityName": "长沙",
        "routeCityAdcode": "430100",
        "areas": [
            {"areaLabel": "长沙五一广场附近", "center": {"longitudeGcj02": 112.9812, "latitudeGcj02": 28.1950}},
            {"areaLabel": "长沙太平老街附近", "center": {"longitudeGcj02": 112.9768, "latitudeGcj02": 28.1933}},
            {"areaLabel": "长沙岳麓山附近", "center": {"longitudeGcj02": 112.9441, "latitudeGcj02": 28.1879}},
        ],
    },
    "xian": {
        "routeCityName": "西安",
        "routeCityAdcode": "610100",
        "areas": [
            {"areaLabel": "西安钟楼附近", "center": {"longitudeGcj02": 108.9402, "latitudeGcj02": 34.2592}},
            {"areaLabel": "西安大雁塔附近", "center": {"longitudeGcj02": 108.9644, "latitudeGcj02": 34.2183}},
            {"areaLabel": "西安小寨附近", "center": {"longitudeGcj02": 108.9470, "latitudeGcj02": 34.2297}},
        ],
    },
    "tianjin": {
        "routeCityName": "天津",
        "routeCityAdcode": "120000",
        "areas": [
            {"areaLabel": "天津五大道附近", "center": {"longitudeGcj02": 117.2076, "latitudeGcj02": 39.1158}},
            {"areaLabel": "天津意式风情区附近", "center": {"longitudeGcj02": 117.2061, "latitudeGcj02": 39.1371}},
            {"areaLabel": "天津滨江道附近", "center": {"longitudeGcj02": 117.1994, "latitudeGcj02": 39.1236}},
        ],
    },
    "jinan": {
        "routeCityName": "济南",
        "routeCityAdcode": "370100",
        "areas": [
            {"areaLabel": "济南趵突泉附近", "center": {"longitudeGcj02": 117.0157, "latitudeGcj02": 36.6612}},
            {"areaLabel": "济南宽厚里附近", "center": {"longitudeGcj02": 117.0321, "latitudeGcj02": 36.6637}},
            {"areaLabel": "济南大明湖附近", "center": {"longitudeGcj02": 117.0228, "latitudeGcj02": 36.6775}},
        ],
    },
    "zhengzhou": {
        "routeCityName": "郑州",
        "routeCityAdcode": "410100",
        "areas": [
            {"areaLabel": "郑州二七广场附近", "center": {"longitudeGcj02": 113.6654, "latitudeGcj02": 34.7524}},
            {"areaLabel": "郑州国贸360附近", "center": {"longitudeGcj02": 113.6807, "latitudeGcj02": 34.7858}},
            {"areaLabel": "郑州郑东新区CBD附近", "center": {"longitudeGcj02": 113.7268, "latitudeGcj02": 34.7669}},
        ],
    },
    "hefei": {
        "routeCityName": "合肥",
        "routeCityAdcode": "340100",
        "areas": [
            {"areaLabel": "合肥淮河路步行街附近", "center": {"longitudeGcj02": 117.2943, "latitudeGcj02": 31.8639}},
            {"areaLabel": "合肥包公园附近", "center": {"longitudeGcj02": 117.3011, "latitudeGcj02": 31.8540}},
            {"areaLabel": "合肥天鹅湖附近", "center": {"longitudeGcj02": 117.2196, "latitudeGcj02": 31.8215}},
        ],
    },
    "fuzhou": {
        "routeCityName": "福州",
        "routeCityAdcode": "350100",
        "areas": [
            {"areaLabel": "福州三坊七巷附近", "center": {"longitudeGcj02": 119.2947, "latitudeGcj02": 26.0862}},
            {"areaLabel": "福州上下杭附近", "center": {"longitudeGcj02": 119.3130, "latitudeGcj02": 26.0523}},
            {"areaLabel": "福州西湖公园附近", "center": {"longitudeGcj02": 119.2864, "latitudeGcj02": 26.0942}},
        ],
    },
    "nanchang": {
        "routeCityName": "南昌",
        "routeCityAdcode": "360100",
        "areas": [
            {"areaLabel": "南昌滕王阁附近", "center": {"longitudeGcj02": 115.8817, "latitudeGcj02": 28.6820}},
            {"areaLabel": "南昌万寿宫附近", "center": {"longitudeGcj02": 115.8911, "latitudeGcj02": 28.6777}},
            {"areaLabel": "南昌八一广场附近", "center": {"longitudeGcj02": 115.9040, "latitudeGcj02": 28.6753}},
        ],
    },
    "kunming": {
        "routeCityName": "昆明",
        "routeCityAdcode": "530100",
        "areas": [
            {"areaLabel": "昆明翠湖附近", "center": {"longitudeGcj02": 102.7041, "latitudeGcj02": 25.0502}},
            {"areaLabel": "昆明南屏街附近", "center": {"longitudeGcj02": 102.7139, "latitudeGcj02": 25.0389}},
            {"areaLabel": "昆明公园1903附近", "center": {"longitudeGcj02": 102.6607, "latitudeGcj02": 25.0016}},
        ],
    },
    "guiyang": {
        "routeCityName": "贵阳",
        "routeCityAdcode": "520100",
        "areas": [
            {"areaLabel": "贵阳甲秀楼附近", "center": {"longitudeGcj02": 106.7200, "latitudeGcj02": 26.5739}},
            {"areaLabel": "贵阳青云市集附近", "center": {"longitudeGcj02": 106.7137, "latitudeGcj02": 26.5668}},
            {"areaLabel": "贵阳黔灵山公园附近", "center": {"longitudeGcj02": 106.6955, "latitudeGcj02": 26.6022}},
        ],
    },
    "haikou": {
        "routeCityName": "海口",
        "routeCityAdcode": "460100",
        "areas": [
            {"areaLabel": "海口骑楼老街附近", "center": {"longitudeGcj02": 110.3431, "latitudeGcj02": 20.0453}},
            {"areaLabel": "海口万绿园附近", "center": {"longitudeGcj02": 110.3176, "latitudeGcj02": 20.0334}},
            {"areaLabel": "海口云洞图书馆附近", "center": {"longitudeGcj02": 110.2872, "latitudeGcj02": 20.0356}},
        ],
    },
    "sanya": {
        "routeCityName": "三亚",
        "routeCityAdcode": "460200",
        "areas": [
            {"areaLabel": "三亚大东海附近", "center": {"longitudeGcj02": 109.5229, "latitudeGcj02": 18.2208}},
            {"areaLabel": "三亚第一市场附近", "center": {"longitudeGcj02": 109.5119, "latitudeGcj02": 18.2529}},
            {"areaLabel": "三亚海月广场附近", "center": {"longitudeGcj02": 109.4987, "latitudeGcj02": 18.2616}},
        ],
    },
    "zhuhai": {
        "routeCityName": "珠海",
        "routeCityAdcode": "440400",
        "areas": [
            {"areaLabel": "珠海情侣路附近", "center": {"longitudeGcj02": 113.5848, "latitudeGcj02": 22.2559}},
            {"areaLabel": "珠海香洲港附近", "center": {"longitudeGcj02": 113.5796, "latitudeGcj02": 22.2734}},
            {"areaLabel": "珠海北山大院附近", "center": {"longitudeGcj02": 113.5202, "latitudeGcj02": 22.2264}},
        ],
    },
    "jiaxing": {
        "routeCityName": "嘉兴",
        "routeCityAdcode": "330400",
        "areas": [
            {"areaLabel": "嘉兴南湖附近", "center": {"longitudeGcj02": 120.7623, "latitudeGcj02": 30.7485}},
            {"areaLabel": "嘉兴月河历史街区附近", "center": {"longitudeGcj02": 120.7556, "latitudeGcj02": 30.7695}},
            {"areaLabel": "嘉兴子城附近", "center": {"longitudeGcj02": 120.7539, "latitudeGcj02": 30.7587}},
        ],
    },
    "huzhou": {
        "routeCityName": "湖州",
        "routeCityAdcode": "330500",
        "areas": [
            {"areaLabel": "湖州衣裳街附近", "center": {"longitudeGcj02": 120.1024, "latitudeGcj02": 30.8677}},
            {"areaLabel": "湖州小西街附近", "center": {"longitudeGcj02": 120.0948, "latitudeGcj02": 30.8666}},
            {"areaLabel": "湖州太湖月亮广场附近", "center": {"longitudeGcj02": 120.1071, "latitudeGcj02": 30.9492}},
        ],
    },
    "yantai": {
        "routeCityName": "烟台",
        "routeCityAdcode": "370600",
        "areas": [
            {"areaLabel": "烟台朝阳街附近", "center": {"longitudeGcj02": 121.3905, "latitudeGcj02": 37.5401}},
            {"areaLabel": "烟台所城里附近", "center": {"longitudeGcj02": 121.3861, "latitudeGcj02": 37.5358}},
            {"areaLabel": "烟台滨海广场附近", "center": {"longitudeGcj02": 121.4011, "latitudeGcj02": 37.5415}},
        ],
    },
    "lanzhou": {
        "routeCityName": "兰州",
        "routeCityAdcode": "620100",
        "areas": [
            {"areaLabel": "兰州张掖路步行街附近", "center": {"longitudeGcj02": 103.8258, "latitudeGcj02": 36.0611}},
            {"areaLabel": "兰州中山桥附近", "center": {"longitudeGcj02": 103.8185, "latitudeGcj02": 36.0668}},
            {"areaLabel": "兰州水车博览园附近", "center": {"longitudeGcj02": 103.8463, "latitudeGcj02": 36.0636}},
        ],
    },
}


DEFAULT_CITY_KEYS = tuple(CITY_PRESETS.keys())

INTEREST_TAG_CODES = (
    "FOOD",
    "COFFEE",
    "MUSEUM",
    "SCENIC",
    "PHOTO",
    "SHOPPING",
    "NIGHT",
    "LOCAL",
)


PERSONA_ARCHETYPES = [
    {
        "name": "low_budget_local",
        "distanceSensitivity": 0.55,
        "budgetSensitivity": 0.90,
        "transferSensitivity": 0.50,
        "hiddenGemAffinity": 0.75,
        "tagAffinities": {"LOCAL": 0.90, "FOOD": 0.80, "COFFEE": 0.70, "SCENIC": 0.25},
    },
    {
        "name": "classic_first_timer",
        "distanceSensitivity": 0.45,
        "budgetSensitivity": 0.45,
        "transferSensitivity": 0.45,
        "hiddenGemAffinity": 0.20,
        "tagAffinities": {"SCENIC": 0.92, "MUSEUM": 0.72, "PHOTO": 0.65, "LOCAL": 0.35},
    },
    {
        "name": "photo_citywalker",
        "distanceSensitivity": 0.40,
        "budgetSensitivity": 0.45,
        "transferSensitivity": 0.35,
        "hiddenGemAffinity": 0.55,
        "tagAffinities": {"PHOTO": 0.92, "LOCAL": 0.68, "COFFEE": 0.58, "SCENIC": 0.52},
    },
    {
        "name": "slow_pace_rest",
        "distanceSensitivity": 0.88,
        "budgetSensitivity": 0.55,
        "transferSensitivity": 0.70,
        "hiddenGemAffinity": 0.45,
        "tagAffinities": {"COFFEE": 0.82, "FOOD": 0.66, "LOCAL": 0.55, "SCENIC": 0.40},
    },
    {
        "name": "night_food",
        "distanceSensitivity": 0.45,
        "budgetSensitivity": 0.50,
        "transferSensitivity": 0.48,
        "hiddenGemAffinity": 0.62,
        "tagAffinities": {"NIGHT": 0.90, "FOOD": 0.88, "LOCAL": 0.70, "PHOTO": 0.55},
    },
    {
        "name": "museum_culture",
        "distanceSensitivity": 0.50,
        "budgetSensitivity": 0.40,
        "transferSensitivity": 0.45,
        "hiddenGemAffinity": 0.42,
        "tagAffinities": {"MUSEUM": 0.92, "SCENIC": 0.70, "COFFEE": 0.45, "PHOTO": 0.40},
    },
    {
        "name": "food_explorer",
        "distanceSensitivity": 0.50,
        "budgetSensitivity": 0.58,
        "transferSensitivity": 0.42,
        "hiddenGemAffinity": 0.72,
        "tagAffinities": {"FOOD": 0.94, "LOCAL": 0.82, "COFFEE": 0.62, "SCENIC": 0.28},
    },
    {
        "name": "transit_averse",
        "distanceSensitivity": 0.65,
        "budgetSensitivity": 0.50,
        "transferSensitivity": 0.88,
        "hiddenGemAffinity": 0.48,
        "tagAffinities": {"LOCAL": 0.70, "COFFEE": 0.55, "SCENIC": 0.52, "FOOD": 0.50},
    },
    {
        "name": "high_energy_mixed",
        "distanceSensitivity": 0.20,
        "budgetSensitivity": 0.35,
        "transferSensitivity": 0.25,
        "hiddenGemAffinity": 0.58,
        "tagAffinities": {"LOCAL": 0.72, "SCENIC": 0.68, "PHOTO": 0.66, "FOOD": 0.62},
    },
    {
        "name": "budget_classic",
        "distanceSensitivity": 0.55,
        "budgetSensitivity": 0.86,
        "transferSensitivity": 0.55,
        "hiddenGemAffinity": 0.28,
        "tagAffinities": {"SCENIC": 0.85, "MUSEUM": 0.62, "FOOD": 0.50, "PHOTO": 0.45},
    },
    {
        "name": "hidden_gem_photo",
        "distanceSensitivity": 0.42,
        "budgetSensitivity": 0.52,
        "transferSensitivity": 0.38,
        "hiddenGemAffinity": 0.90,
        "tagAffinities": {"LOCAL": 0.88, "PHOTO": 0.82, "COFFEE": 0.65, "SCENIC": 0.20},
    },
    {
        "name": "balanced_family",
        "distanceSensitivity": 0.72,
        "budgetSensitivity": 0.62,
        "transferSensitivity": 0.65,
        "hiddenGemAffinity": 0.35,
        "tagAffinities": {"SCENIC": 0.72, "FOOD": 0.62, "MUSEUM": 0.58, "LOCAL": 0.48},
    },
    {
        "name": "foodie_heavy",
        "distanceSensitivity": 0.42,
        "budgetSensitivity": 0.48,
        "transferSensitivity": 0.40,
        "hiddenGemAffinity": 0.78,
        "tagAffinities": {"FOOD": 0.96, "NIGHT": 0.82, "LOCAL": 0.80, "COFFEE": 0.58},
    },
    {
        "name": "shopping_enthusiast",
        "distanceSensitivity": 0.38,
        "budgetSensitivity": 0.28,
        "transferSensitivity": 0.35,
        "hiddenGemAffinity": 0.42,
        "tagAffinities": {"SHOPPING": 0.94, "LOCAL": 0.70, "PHOTO": 0.66, "FOOD": 0.64, "SCENIC": 0.45},
    },
    {
        "name": "play_enthusiast",
        "distanceSensitivity": 0.22,
        "budgetSensitivity": 0.36,
        "transferSensitivity": 0.25,
        "hiddenGemAffinity": 0.64,
        "tagAffinities": {"SCENIC": 0.78, "LOCAL": 0.76, "PHOTO": 0.74, "NIGHT": 0.66},
    },
    {
        "name": "quiet_culture_reader",
        "distanceSensitivity": 0.82,
        "budgetSensitivity": 0.42,
        "transferSensitivity": 0.62,
        "hiddenGemAffinity": 0.58,
        "tagAffinities": {"MUSEUM": 0.94, "COFFEE": 0.78, "SCENIC": 0.58, "LOCAL": 0.45},
    },
    {
        "name": "market_snacker",
        "distanceSensitivity": 0.36,
        "budgetSensitivity": 0.72,
        "transferSensitivity": 0.40,
        "hiddenGemAffinity": 0.82,
        "tagAffinities": {"FOOD": 0.92, "LOCAL": 0.88, "NIGHT": 0.78, "COFFEE": 0.38},
    },
    {
        "name": "comfort_first_timer",
        "distanceSensitivity": 0.90,
        "budgetSensitivity": 0.46,
        "transferSensitivity": 0.82,
        "hiddenGemAffinity": 0.18,
        "tagAffinities": {"SCENIC": 0.88, "MUSEUM": 0.72, "FOOD": 0.55, "PHOTO": 0.42},
    },
    {
        "name": "night_photo_chaser",
        "distanceSensitivity": 0.34,
        "budgetSensitivity": 0.40,
        "transferSensitivity": 0.36,
        "hiddenGemAffinity": 0.66,
        "tagAffinities": {"NIGHT": 0.94, "PHOTO": 0.88, "FOOD": 0.64, "LOCAL": 0.58},
    },
    {
        "name": "coffee_hidden_gem",
        "distanceSensitivity": 0.48,
        "budgetSensitivity": 0.52,
        "transferSensitivity": 0.44,
        "hiddenGemAffinity": 0.88,
        "tagAffinities": {"COFFEE": 0.92, "LOCAL": 0.80, "PHOTO": 0.54, "MUSEUM": 0.42},
    },
    {
        "name": "low_walk_tolerance",
        "distanceSensitivity": 0.94,
        "budgetSensitivity": 0.58,
        "transferSensitivity": 0.74,
        "hiddenGemAffinity": 0.36,
        "tagAffinities": {"FOOD": 0.70, "COFFEE": 0.68, "SCENIC": 0.55, "LOCAL": 0.48},
    },
    {
        "name": "free_spending_photo_food",
        "distanceSensitivity": 0.30,
        "budgetSensitivity": 0.16,
        "transferSensitivity": 0.28,
        "hiddenGemAffinity": 0.54,
        "tagAffinities": {"SHOPPING": 0.88, "PHOTO": 0.86, "FOOD": 0.82, "SCENIC": 0.62, "LOCAL": 0.58},
    },
    {
        "name": "budget_hidden_classic",
        "distanceSensitivity": 0.62,
        "budgetSensitivity": 0.94,
        "transferSensitivity": 0.58,
        "hiddenGemAffinity": 0.68,
        "tagAffinities": {"SCENIC": 0.70, "LOCAL": 0.70, "FOOD": 0.64, "MUSEUM": 0.58},
    },
]


REQUEST_TEMPLATES = [
    {"routeGoal": "LOCAL", "transportProfile": "WALK_SUBWAY", "budgetLevel": "NORMAL", "interestTags": ["LOCAL", "FOOD", "COFFEE"], "durationMinutes": 240, "hour": 14},
    {"routeGoal": "CLASSIC", "transportProfile": "WALK_SUBWAY", "budgetLevel": "NORMAL", "interestTags": ["SCENIC", "MUSEUM", "PHOTO"], "durationMinutes": 300, "hour": 10},
    {"routeGoal": "LOW_BUDGET", "transportProfile": "WALK_ONLY", "budgetLevel": "LOW", "interestTags": ["LOCAL", "FOOD", "COFFEE"], "durationMinutes": 180, "hour": 13},
    {"routeGoal": "NIGHT", "transportProfile": "WALK_TAXI", "budgetLevel": "NORMAL", "interestTags": ["NIGHT", "FOOD", "PHOTO"], "durationMinutes": 240, "hour": 17},
    {"routeGoal": "PHOTO", "transportProfile": "WALK_TRANSIT", "budgetLevel": "NORMAL", "interestTags": ["PHOTO", "LOCAL", "SCENIC"], "durationMinutes": 240, "hour": 15},
    {"routeGoal": "STEADY", "transportProfile": "WALK_BUS", "budgetLevel": "NORMAL", "interestTags": ["MUSEUM", "COFFEE", "FOOD"], "durationMinutes": 210, "hour": 11},
    {"routeGoal": "LOCAL", "transportProfile": "BIKE_SUBWAY", "budgetLevel": "FLEXIBLE", "interestTags": ["LOCAL", "SHOPPING", "PHOTO", "COFFEE"], "durationMinutes": 360, "hour": 10},
    {"routeGoal": "CLASSIC", "transportProfile": "WALK_TAXI", "budgetLevel": "FLEXIBLE", "interestTags": ["SCENIC", "PHOTO", "FOOD"], "durationMinutes": 420, "hour": 9},
    {"routeGoal": "LOCAL", "transportProfile": "WALK_ONLY", "budgetLevel": "NORMAL", "interestTags": ["FOOD", "LOCAL", "COFFEE"], "durationMinutes": 150, "hour": 11},
    {"routeGoal": "LOCAL", "transportProfile": "WALK_BUS", "budgetLevel": "LOW", "interestTags": ["LOCAL", "FOOD", "PHOTO"], "durationMinutes": 300, "hour": 9},
    {"routeGoal": "PHOTO", "transportProfile": "WALK_ONLY", "budgetLevel": "NORMAL", "interestTags": ["PHOTO", "COFFEE", "LOCAL"], "durationMinutes": 180, "hour": 16},
    {"routeGoal": "NIGHT", "transportProfile": "WALK_SUBWAY", "budgetLevel": "FLEXIBLE", "interestTags": ["NIGHT", "FOOD", "LOCAL"], "durationMinutes": 300, "hour": 18},
    {"routeGoal": "STEADY", "transportProfile": "WALK_ONLY", "budgetLevel": "LOW", "interestTags": ["COFFEE", "LOCAL", "MUSEUM"], "durationMinutes": 150, "hour": 10},
    {"routeGoal": "CLASSIC", "transportProfile": "WALK_BUS", "budgetLevel": "LOW", "interestTags": ["SCENIC", "MUSEUM", "COFFEE"], "durationMinutes": 240, "hour": 8},
    {"routeGoal": "LOCAL", "transportProfile": "WALK_TAXI", "budgetLevel": "FLEXIBLE", "interestTags": ["SHOPPING", "LOCAL", "FOOD", "NIGHT"], "durationMinutes": 420, "hour": 13},
    {"routeGoal": "LOW_BUDGET", "transportProfile": "WALK_SUBWAY", "budgetLevel": "LOW", "interestTags": ["SCENIC", "LOCAL", "FOOD"], "durationMinutes": 360, "hour": 9},
    {"routeGoal": "PHOTO", "transportProfile": "BIKE_SUBWAY", "budgetLevel": "FLEXIBLE", "interestTags": ["PHOTO", "SCENIC", "LOCAL"], "durationMinutes": 360, "hour": 14},
    {"routeGoal": "STEADY", "transportProfile": "WALK_TRANSIT", "budgetLevel": "NORMAL", "interestTags": ["MUSEUM", "SCENIC", "COFFEE"], "durationMinutes": 300, "hour": 12},
    {"routeGoal": "NIGHT", "transportProfile": "WALK_ONLY", "budgetLevel": "LOW", "interestTags": ["NIGHT", "FOOD", "LOCAL"], "durationMinutes": 180, "hour": 19},
    {"routeGoal": "CLASSIC", "transportProfile": "WALK_TRANSIT", "budgetLevel": "NORMAL", "interestTags": ["SCENIC", "LOCAL", "PHOTO"], "durationMinutes": 360, "hour": 11},
    {"routeGoal": "STEADY", "transportProfile": "WALK_TAXI", "budgetLevel": "NORMAL", "interestTags": ["FOOD", "COFFEE", "LOCAL"], "durationMinutes": 180, "hour": 12},
    {"routeGoal": "LOCAL", "transportProfile": "WALK_ONLY", "budgetLevel": "LOW", "interestTags": ["LOCAL", "FOOD", "NIGHT"], "durationMinutes": 210, "hour": 18},
    {"routeGoal": "CLASSIC", "transportProfile": "WALK_SUBWAY", "budgetLevel": "FLEXIBLE", "interestTags": ["SCENIC", "MUSEUM", "FOOD"], "durationMinutes": 480, "hour": 9},
    {"routeGoal": "PHOTO", "transportProfile": "WALK_TAXI", "budgetLevel": "FLEXIBLE", "interestTags": ["PHOTO", "SCENIC", "COFFEE"], "durationMinutes": 240, "hour": 15},
    {"routeGoal": "LOW_BUDGET", "transportProfile": "WALK_BUS", "budgetLevel": "LOW", "interestTags": ["LOCAL", "COFFEE", "MUSEUM"], "durationMinutes": 240, "hour": 10},
    {"routeGoal": "STEADY", "transportProfile": "WALK_ONLY", "budgetLevel": "NORMAL", "interestTags": ["MUSEUM", "SCENIC", "FOOD"], "durationMinutes": 120, "hour": 9},
    {"routeGoal": "NIGHT", "transportProfile": "WALK_TAXI", "budgetLevel": "FLEXIBLE", "interestTags": ["NIGHT", "PHOTO", "FOOD"], "durationMinutes": 360, "hour": 17},
    {"routeGoal": "LOCAL", "transportProfile": "WALK_TRANSIT", "budgetLevel": "NORMAL", "interestTags": ["LOCAL", "MUSEUM", "COFFEE"], "durationMinutes": 300, "hour": 13},
    {"routeGoal": "PHOTO", "transportProfile": "WALK_ONLY", "budgetLevel": "LOW", "interestTags": ["PHOTO", "LOCAL", "FOOD"], "durationMinutes": 150, "hour": 8},
    {"routeGoal": "CLASSIC", "transportProfile": "BIKE_SUBWAY", "budgetLevel": "NORMAL", "interestTags": ["SCENIC", "PHOTO", "COFFEE"], "durationMinutes": 300, "hour": 14},
    {"routeGoal": "LOW_BUDGET", "transportProfile": "WALK_ONLY", "budgetLevel": "LOW", "interestTags": ["FOOD", "LOCAL", "PHOTO"], "durationMinutes": 120, "hour": 12},
    {"routeGoal": "STEADY", "transportProfile": "WALK_BUS", "budgetLevel": "FLEXIBLE", "interestTags": ["COFFEE", "FOOD", "SCENIC"], "durationMinutes": 420, "hour": 10},
    {"routeGoal": "LOCAL", "transportProfile": "WALK_TAXI", "budgetLevel": "NORMAL", "interestTags": ["LOCAL", "SHOPPING", "PHOTO", "MUSEUM"], "durationMinutes": 360, "hour": 9},
    {"routeGoal": "NIGHT", "transportProfile": "WALK_SUBWAY", "budgetLevel": "LOW", "interestTags": ["NIGHT", "LOCAL", "COFFEE"], "durationMinutes": 240, "hour": 20},
    {"routeGoal": "CLASSIC", "transportProfile": "WALK_ONLY", "budgetLevel": "NORMAL", "interestTags": ["SCENIC", "MUSEUM", "LOCAL"], "durationMinutes": 180, "hour": 13},
    {"routeGoal": "PHOTO", "transportProfile": "WALK_TRANSIT", "budgetLevel": "NORMAL", "interestTags": ["PHOTO", "NIGHT", "FOOD"], "durationMinutes": 300, "hour": 16},
]
