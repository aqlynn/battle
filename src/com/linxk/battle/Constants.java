package com.linxk.battle;

public class Constants {
	public static final long SHOOT_DELAY_MS = 1000;//开枪中枪有效相差时间阈值
	public static final int ZOMBIE_MODE_SCORE_INC = 1;//僵尸模式杀敌加分
	public static final String MEMCACHED_PHONENUMBER_TAG = "_phonenumber";//手机号绑定后缀
	public static final String MEMCACHED_HP_TAG = "_hp";//血量后缀
	public static final String MEMCACHED_SCORE_TAG = "_score";//本次得分后缀
	public static final String MEMCACHED_GUN_PREFIX = "gun_";//枪信息前缀
	public static final String MEMCACHED_GUN_USERID_TAG = "_userid";//枪绑定用户id后缀
	public static final String MEMCACHED_GUN_BULLETS_TAG = "_bullets";//枪子弹数后缀
	public static final String MEMCACHED_GUN_SHOT_TIMESTAMP_TAG = "_shottimestamp";//枪子弹数后缀
}
