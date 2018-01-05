package com.linxk.battle;

import com.linxk.battle.memcached.MemcachedClient;
import com.linxk.battle.memcached.SockIOPool;
import com.linxk.battle.util.JdbcUtils;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.linxk.battle.BattleProtos.ClientType;
import com.linxk.battle.BattleProtos.GlobalMessage;
import com.linxk.battle.BattleProtos.MessageType;
import com.linxk.battle.BattleProtos.MsgHumanResponseErrorOccurred;
import com.linxk.battle.BattleProtos.MsgHumanResponseGunData;
import com.linxk.battle.BattleProtos.MsgHumanResponseOpSuccess;
import com.linxk.battle.BattleProtos.MsgHumanResponseUserData;


public class BattleServerHandler extends SimpleChannelInboundHandler<GlobalMessage> {
	public static ConcurrentHashMap<String, BattleServerHandler> sessionHandlerMap = new ConcurrentHashMap<String, BattleServerHandler>(); 
	public Channel sessionChannel;
	public String sessionclientid = "";//本次操作关联的用户id
	
	protected static JdbcUtils jdbcUtils = new JdbcUtils();
	
	//protected static MemcachedClient mcc = new MemcachedClient(true,true);
	protected static MemcachedClient mcc = new MemcachedClient();
	// set up connection pool once at class load
	static {
		jdbcUtils.getConnection();
		// server list and weights
		String[] servers =
			{
			  "localhost:11211"
			};

		Integer[] weights = { 1 };

		// grab an instance of our connection pool
		SockIOPool pool = SockIOPool.getInstance();

		// set the servers and the weights
		pool.setServers( servers );
		pool.setWeights( weights );

		// set some TCP settings
		// disable nagle
		// set the read timeout to 3 secs
		// and don't set a connect timeout
		pool.setNagle( false );
		pool.setSocketTO( 3000 );
		pool.setSocketConnectTO( 0 );

		// initialize the connection pool
		pool.initialize();
		//========================================Test=======================================
		mcc.set("gun_gunid12345678_bullets", 100);
		//========================================Test=======================================
	}
	
    @Override
    public void channelRead0(ChannelHandlerContext ctx, GlobalMessage msg) throws Exception {
    	Boolean sendresponse = false;
    	GlobalMessage.Builder builder = GlobalMessage.newBuilder();
    	builder.setClienttype(msg.getClienttype());
    	builder.setClientid(msg.getClientid());
    	if(msg.getClientid().trim().isEmpty()) {
    		System.out.println("null clientid provided!");
    		return;
    	}
    	sessionclientid = msg.getClientid();
    	if(msg.getMsgtype()==MessageType.HumanRequestLoginWithPhone) {
    		//登录绑定
    		String phonenumber = msg.getHumanrequestloginwithphone().getPhonenumber().trim();
    		List<Object> params = new ArrayList<Object>();  
    		params.add(phonenumber);
    		Map<String, Object> userinfo = null;
    		try {
				userinfo = jdbcUtils.findSimpleResult("select * from userinfo where phonenumber=?", params);
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
    		if ((userinfo != null) && (userinfo.size()>0)) {
    			//手机号有记录
				Object o = mcc.get(sessionclientid+Constants.MEMCACHED_PHONENUMBER_TAG);
				if((o == null) || (o.toString().isEmpty())) {
					//未绑定
					boolean ret = mcc.set(sessionclientid+Constants.MEMCACHED_PHONENUMBER_TAG, phonenumber);
					if (ret) {
						sessionChannel = ctx.channel();
						sessionHandlerMap.put(sessionclientid, this);
						
						builder.setMsgtype(MessageType.HumanResponseOpSuccess);
						MsgHumanResponseOpSuccess.Builder responsebuilder = MsgHumanResponseOpSuccess.newBuilder();
		        		responsebuilder.setLastmsg(msg.getMsgtype());
		        		responsebuilder.setSuccmsg("登录成功");
		        		builder.setHumanresponseopsuccess(responsebuilder.build());
					} else {
						//写入绑定失败
						buildError(builder, msg.getMsgtype(), "登录失败！内部错误");
					}
				} else {
					//已绑定
					buildError(builder, msg.getMsgtype(), "重复登录");
				}
    		} else {
    			//手机号没有记录
    			buildError(builder, msg.getMsgtype(), "用户未注册");
    		}
    		sendresponse = true;
    	} else if(msg.getMsgtype()==MessageType.HumanRequestUserData) {
    		sendUserData(sessionclientid);
    	} else if(msg.getMsgtype()==MessageType.HumanRequestGunData) {
    		boolean ok = false;
    		String gunid = msg.getHumanrequestgundata().getGunid();
    		Object oUserid = mcc.get(Constants.MEMCACHED_GUN_PREFIX+gunid+Constants.MEMCACHED_GUN_USERID_TAG);
    		if((oUserid == null)||(!oUserid.toString().equals(sessionclientid))) {
    			//未绑定枪
    			boolean ret = mcc.set(Constants.MEMCACHED_GUN_PREFIX+gunid+Constants.MEMCACHED_GUN_USERID_TAG, sessionclientid);
				if (ret) {
					ok = true;
				} else {
					//写入绑定失败
					buildError(builder, msg.getMsgtype(), "绑定失败！内部错误");
				}
			} else {
				ok = true;
			}
    		if(ok) {
    			Object o = mcc.get(Constants.MEMCACHED_GUN_PREFIX+gunid+Constants.MEMCACHED_GUN_BULLETS_TAG);
    			if(o != null) {
    				builder.setMsgtype(MessageType.HumanResponseGunData);
    				MsgHumanResponseGunData.Builder responsebuilder = MsgHumanResponseGunData.newBuilder();
    				responsebuilder.setGunid(gunid);
    				responsebuilder.setBulletcount(Integer.parseInt(o.toString()));
            		builder.setHumanresponsegundata(responsebuilder.build());
    			} else {
    				//无数据
    				buildError(builder, msg.getMsgtype(), "无效gunid或游戏未开始");
    			}
    		}
			sendresponse = true;
    	} else if(msg.getMsgtype()==MessageType.HumanRequestGunShot) {
    		//开枪
    		boolean error = false;
    		String gunid = msg.getHumanrequestgunshot().getGunid();
    		int count = msg.getHumanrequestgunshot().getCount();
    		Object o = mcc.get(Constants.MEMCACHED_GUN_PREFIX+gunid+Constants.MEMCACHED_GUN_BULLETS_TAG);
			if((o != null)&&(!o.toString().isEmpty())) {
				int bulletcount = Integer.parseInt(o.toString());
				if (bulletcount > 0) {
					//还有子弹
					bulletcount -= count;
					boolean ret = mcc.set(Constants.MEMCACHED_GUN_PREFIX+gunid+Constants.MEMCACHED_GUN_BULLETS_TAG, bulletcount);
					if (ret) {
						ret = mcc.set(Constants.MEMCACHED_GUN_PREFIX+gunid+Constants.MEMCACHED_GUN_SHOT_TIMESTAMP_TAG, System.currentTimeMillis());
						if (!ret) {
							//写入开枪时间戳失败
							buildError(builder, msg.getMsgtype(), "记录失败！内部错误");
							error = true;
						}
					} else {
						//写入减子弹失败
						buildError(builder, msg.getMsgtype(), "记录失败！内部错误");
						error = true;
					}
				}
				if (!error) {
					builder.setMsgtype(MessageType.HumanResponseGunData);
					MsgHumanResponseGunData.Builder responsebuilder = MsgHumanResponseGunData.newBuilder();
					responsebuilder.setGunid(gunid);
					responsebuilder.setBulletcount(bulletcount);
	        		builder.setHumanresponsegundata(responsebuilder.build());
				}
			} else {
				//无数据
				buildError(builder, msg.getMsgtype(), "无效gunid或游戏未开始");
			}
			sendresponse = true;
    	} else if(msg.getMsgtype()==MessageType.ZombieWasShot) {
    		String gunid = msg.getGunidreceived().getGunid();
    		Object o = mcc.get(Constants.MEMCACHED_GUN_PREFIX+gunid+Constants.MEMCACHED_GUN_SHOT_TIMESTAMP_TAG);
			if((o != null)&&(!o.toString().isEmpty())) {
				long timestamp = Long.parseLong(o.toString());
				if (System.currentTimeMillis() - timestamp <= Constants.SHOOT_DELAY_MS) {
					//射中了
					Object oUserid = mcc.get(Constants.MEMCACHED_GUN_PREFIX+gunid+Constants.MEMCACHED_GUN_USERID_TAG);
					if((oUserid != null)&&(!oUserid.toString().isEmpty())) {
						String userid = oUserid.toString();
						Object oScore = mcc.get(userid+Constants.MEMCACHED_SCORE_TAG);
						if((oScore != null)&&(!oScore.toString().isEmpty())) {
							int score = Integer.parseInt(oScore.toString());
							score += Constants.ZOMBIE_MODE_SCORE_INC;
							boolean ret = mcc.set(userid+Constants.MEMCACHED_SCORE_TAG, score);
							if (ret) {
								//通知用户
								sendUserData(userid);
								//通知僵尸
								builder.setMsgtype(MessageType.ZombieDied);
								sendresponse = true;
							} else {
								//写入加分失败
								System.out.println("error! add user("+userid+") score failed!");
							}
						} else {
							//无数据
							System.out.println("error! get user("+userid+") score failed!");
						}
					} else {
						//无数据
						System.out.println("error! get user id by gun id failed!");
					}
				}
			} else {
				//无数据
				System.out.println("error! unknown gun id!");
			}
    	} else if(msg.getMsgtype()==MessageType.ZombieTouchedCard) {
    		String cardid = msg.getCardidreceived().getCardid();
    		System.out.println("zombie "+msg.getClientid()+" touched card:"+cardid);
    	} else if(msg.getMsgtype()==MessageType.HumanRequestLogout) {
    		sessionHandlerMap.remove(sessionclientid);
        	mcc.delete(sessionclientid+Constants.MEMCACHED_PHONENUMBER_TAG);
    	} else {
    		System.out.println("Server: unknown msg type!");
    	}
    	if (sendresponse) {
    		ctx.channel().writeAndFlush(builder.build());
    	}
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
    	sessionHandlerMap.remove(sessionclientid);
    	mcc.delete(sessionclientid+Constants.MEMCACHED_PHONENUMBER_TAG);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        cause.printStackTrace();
        ctx.channel().close();
    }
    
    public void buildError(GlobalMessage.Builder builder, MessageType lastmsg,String errmsg) {
    	builder.setMsgtype(MessageType.HumanResponseErrorOccurred);
		MsgHumanResponseErrorOccurred.Builder responsebuilder = MsgHumanResponseErrorOccurred.newBuilder();
		responsebuilder.setLastmsg(lastmsg);
		responsebuilder.setErrmsg(errmsg);
		builder.setHumanresponseerroroccurred(responsebuilder.build());
    }
    
    public static void sendUserData(String clientid) {
    	BattleServerHandler handler = sessionHandlerMap.get(clientid);
    	if (handler == null) {
    		System.out.println("error! unknown client id!");
    		return;
    	}
    	GlobalMessage.Builder builder = GlobalMessage.newBuilder();
    	builder.setClienttype(ClientType.Human);
    	builder.setClientid(clientid);
    	Object oHp = mcc.get(clientid+Constants.MEMCACHED_HP_TAG);
		Object oScore = mcc.get(clientid+Constants.MEMCACHED_SCORE_TAG);
		if((oHp != null) && (oScore != null)) {
			builder.setMsgtype(MessageType.HumanResponseUserData);
			MsgHumanResponseUserData.Builder responsebuilder = MsgHumanResponseUserData.newBuilder();
    		responsebuilder.setHp(Integer.parseInt(oHp.toString()));
    		responsebuilder.setScore(Integer.parseInt(oScore.toString()));
    		builder.setHumanresponseuserdata(responsebuilder.build());
		} else {
			//无数据
			handler.buildError(builder, MessageType.UnknownMessageType, "无效id或游戏未开始");
		}
		handler.sessionChannel.writeAndFlush(builder.build());
    }
}
