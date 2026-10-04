import org.bukkit.*;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.BlockRedstoneEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import top.e404.eclean.config.Config;
import top.e404.eclean.monitor.RedstoneMonitor;
import top.e404.eclean.maintenance.ChunkUnloader;
import top.e404.eclean.menu.trashcan.TrashcanMenu;
import top.e404.eclean.menu.dense.DenseMenu;
import top.e404.eclean.menu.dense.EntityInfo;
import top.e404.eclean.util.Compatibility;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;

/** Disposable server probe compiled against 1.8 API with Java 8 bytecode. */
public class MatrixProbe extends JavaPlugin implements Listener {
  final List<String> rows=new ArrayList<>();
  final Map<BlockRedstoneEvent,Integer> before=new IdentityHashMap<>();
  final Set<String> unloaded=new HashSet<>();
  int failures,eventsA,eventsB,suppressed,falling;
  World a,b,u; BukkitTask clock;
  String living="living: {enable: false}\n",drop="drop: {enable: false}\n",dense="chunk: {enable: false}\n";
  String red="redstone: {enable: false}\n",unload="chunk_unload: {enable: false}\n";
  boolean restarting;
  final Path checkpoint=Paths.get("phase1.complete");
  void write(Path p,String s){try{Files.write(p,s.getBytes("UTF-8"));}catch(Exception e){throw new RuntimeException(e);}}
  void check(String name,boolean pass,Object detail){
    String row=(pass?"PASS":"FAIL")+"\t"+name+"\t"+detail;rows.add(row);if(!pass)failures++;
    getLogger().info("MATRIX_"+row);write(Paths.get(restarting?"restart-results.tsv":"results.tsv"),String.join("\n",rows)+"\n");
  }
  void later(long ticks,Runnable r){Bukkit.getScheduler().runTaskLater(this,()->{
    try{r.run();}catch(Throwable e){check("unexpected_exception",false,e.toString());e.printStackTrace();Bukkit.shutdown();}
  },ticks);}
  public void onEnable(){Bukkit.getPluginManager().registerEvents(this,this);restarting=Files.exists(checkpoint);later(20,()->{
    check("plugin_enabled",Bukkit.getPluginManager().isPluginEnabled("EClean"),Bukkit.getVersion()+"; Java "+System.getProperty("java.version"));
    if(restarting)restart();else first();
  });}
  Object optional(Object target,String name,Class<?>[] types,Object...args){
    try{return target.getClass().getMethod(name,types).invoke(target,args);}catch(NoSuchMethodException e){return null;}catch(Exception e){throw new RuntimeException(e);}
  }
  void force(Chunk chunk,boolean value){optional(chunk,"setForceLoaded",new Class[]{boolean.class},value);}
  World world(String name){
    World w=new WorldCreator(name).type(WorldType.FLAT).generateStructures(false).createWorld();
    optional(w,"setKeepSpawnInMemory",new Class[]{boolean.class},false);
    w.setGameRuleValue("doMobSpawning","false");w.setSpawnFlags(true,true);w.setDifficulty(Difficulty.NORMAL);w.setTime(18000);return w;
  }
  String yaml(){return "debug: false\nupdate: false\nworld_rules: true\nduration: 9999999\nmessage: {}\ntrashcan: {enable: false, collect: false}\nno_online: {clean: true, message: false}\n"+living+drop+dense+red+unload;}
  void config(){write(Paths.get("plugins/EClean/config.yml"),yaml());Config.INSTANCE.load(Bukkit.getConsoleSender());}
  void cmd(String s){if(!Bukkit.dispatchCommand(Bukkit.getConsoleSender(),s))check("dispatch",false,s);}
  Entity spawn(World w,EntityType type,boolean named){
    w.getChunkAt(0,0).load();Entity e=w.spawnEntity(new Location(w,8,72,8),type);
    if(e instanceof LivingEntity)((LivingEntity)e).setRemoveWhenFarAway(false);
    if(named)e.setCustomName("protected");
    if(!e.isValid())throw new IllegalStateException("Probe entity not activated: "+w.getName()+" "+type);
    return e;
  }
  void clear(){for(World w:Arrays.asList(a,b))for(Entity e:w.getEntities())if(!(e instanceof Player))e.remove();}
  long valid(List<Entity> entities){return entities.stream().filter(Entity::isValid).count();}
  void first(){
    a=world("it_a");b=world("it_b");u=world("it_unload");
    for(World w:Arrays.asList(a,b)){w.getChunkAt(0,0).load();force(w.getChunkAt(0,0),true);}
    clear();config();
    check("optional_services_default_off",RedstoneMonitor.INSTANCE.getWindow$EClean()==null&&ChunkUnloader.INSTANCE.getPolicy$EClean()==null,"defaults");
    check("sound_aliases",Compatibility.INSTANCE.sound("ENTITY_BLAZE_DEATH","BLAZE_DEATH")!=null&&Compatibility.INSTANCE.sound("BLOCK_STONE_BUTTON_CLICK_ON","CLICK")!=null&&Compatibility.INSTANCE.sound("ENTITY_EXPERIENCE_ORB_PICKUP","ORB_PICKUP")!=null,"three aliases");
    TrashcanMenu tm=new TrashcanMenu();tm.onInit();DenseMenu dm=new DenseMenu(new ArrayList<>(Arrays.asList(new EntityInfo(EntityType.ZOMBIE,1,a.getChunkAt(0,0)))));dm.onInit();
    check("menu_construct_update",tm.getInv().getSize()==54&&dm.getInv().getSize()==54,"trash and dense inventories");
    later(40,this::rules);
  }
  void rules(){
    living="living:\n  enable: true\n  is_black: true\n  match: [ZOMBIE]\n  worlds:\n    it_a: {match: [SHEEP], settings: {name: true}}\n  entities:\n    COW: {clean: true}\n    SHEEP: {clean: false}\n    ZOMBIE: {settings: {name: false}}\n";config();
    Entity az=spawn(a,EntityType.ZOMBIE,false),bz=spawn(b,EntityType.ZOMBIE,false),s=spawn(a,EntityType.SHEEP,false),c=spawn(a,EntityType.COW,false);
    cmd("eclean clean entity it_a");check("world_entity_default_priority",az.isValid()&&bz.isValid()&&s.isValid()&&!c.isValid(),"A zombie="+az.isValid()+", B zombie="+bz.isValid()+", sheep="+s.isValid()+", cow="+c.isValid());
    cmd("eclean clean entity");check("other_world_default",!bz.isValid()&&az.isValid(),"B uses default");
    cmd("eclean worldrules off");cmd("eclean clean entity it_a");check("worldrules_off_restores_default",!Config.INSTANCE.getConfig().getWorldRules()&&!az.isValid()&&s.isValid(),"overrides bypassed");
    cmd("eclean worldrules on");cmd("eclean worldrules status");Config.INSTANCE.load(Bukkit.getConsoleSender());check("worldrules_on_persisted",Config.INSTANCE.getConfig().getWorldRules(),"disk reload");clear();
    living=living.replace("match: [SHEEP]","match: [ZOMBIE, SHEEP]").replace("SHEEP: {clean: false}","SHEEP: {clean: true}");config();
    Entity z=spawn(a,EntityType.ZOMBIE,true),sheep=spawn(a,EntityType.SHEEP,true);cmd("eclean clean entity it_a");check("field_priority",z.isValid()&&!sheep.isValid(),"entity name=false beats world true");clear();
    living="living: {enable: true, is_black: false, match: []}\n";config();
    Entity vehicle=spawn(a,EntityType.COW,false),rider=spawn(a,EntityType.ZOMBIE,false);
    try{Entity.class.getMethod("addPassenger",Entity.class).invoke(vehicle,rider);}catch(NoSuchMethodException e){optional(vehicle,"setPassenger",new Class[]{Entity.class},rider);}catch(Exception e){throw new RuntimeException(e);}
    cmd("eclean clean entity it_a");check("passenger_protection",vehicle.isValid()&&rider.isValid(),"both retained");clear();living="living: {enable: false}\n";
    drop="drop:\n  enable: true\n  is_black: false\n  match: []\n  worlds:\n    it_a: {lore: true}\n  materials:\n    STONE: {lore: false}\n    DIAMOND: {clean: false}\n";config();
    Item stone=item(a,Material.STONE),dirt=item(a,Material.DIRT),other=item(b,Material.DIRT),diamond=item(a,Material.DIAMOND);cmd("eclean clean drop");check("material_world_default_priority",!stone.isValid()&&dirt.isValid()&&!other.isValid()&&diamond.isValid(),"lore and exact material rules");clear();
    drop="drop: {enable: true, is_black: false, match: [], written_book: true}\n";config();Material book=Material.matchMaterial("WRITABLE_BOOK");if(book==null)book=Material.matchMaterial("BOOK_AND_QUILL");
    ItemStack stack=new ItemStack(book);BookMeta meta=(BookMeta)stack.getItemMeta();meta.addPage("retain");stack.setItemMeta(meta);Item page=a.dropItem(new Location(a,9,72,9),stack);cmd("eclean clean drop it_a");check("book_protection_alias",page.isValid(),book);clear();drop="drop: {enable: false}\n";
    dense="chunk:\n  enable: true\n  limit: {\"ZOMBIE|SHEEP\": 5}\n  worlds:\n    it_a: {limit: {\"ZOMBIE|SHEEP\": 0}}\n  entities:\n    ZOMBIE: {limit: 2}\n";config();
    List<Entity> zs=new ArrayList<>(),ss=new ArrayList<>(),bs=new ArrayList<>();for(int i=0;i<4;i++)zs.add(spawn(a,EntityType.ZOMBIE,false));for(int i=0;i<3;i++){ss.add(spawn(a,EntityType.SHEEP,false));bs.add(spawn(b,EntityType.SHEEP,false));}
    cmd("eclean clean chunk");check("dense_exact_and_zero_limit",valid(zs)==2&&valid(ss)==0&&valid(bs)==3,valid(zs)+","+valid(ss)+","+valid(bs));
    cmd("eclean clean chunk");check("dense_same_tick_recheck",valid(zs)==2,valid(zs));cmd("eclean stats it_a");check("world_stats_command",true,"returned without linkage error");clear();
    Object active=Config.INSTANCE.getConfig();write(Paths.get("plugins/EClean/config.yml"),yaml().replace("ZOMBIE: {limit: 2}","ZOMBIE: {limit: -1}"));
    try{Config.INSTANCE.load(Bukkit.getConsoleSender());check("invalid_config_rejected",false,"accepted");}catch(Exception e){check("invalid_config_rejected",Config.INSTANCE.getConfig()==active,e.getClass().getSimpleName());}
    dense="chunk: {enable: false}\n";red=red("report",false);config();commands();redstone();
  }
  Item item(World w,Material type){ItemStack s=new ItemStack(type);org.bukkit.inventory.meta.ItemMeta m=s.getItemMeta();m.setLore(Arrays.asList("lore"));s.setItemMeta(m);return w.dropItem(new Location(w,9,72,9),s);}
  void commands(){
    List<String> denied=new ArrayList<>();CommandSender sender=(CommandSender)Proxy.newProxyInstance(getClassLoader(),new Class[]{CommandSender.class},(p,m,args)->{
      if(m.getName().equals("sendMessage")){denied.add(String.valueOf(args[0]));return null;}if(m.getName().equals("getName"))return "no-permission";if(m.getReturnType()==boolean.class)return false;return null;
    });
    Bukkit.getPluginCommand("eclean").execute(sender,"eclean",new String[]{"redstone","on"});check("toggle_permission_guard",!Config.INSTANCE.getConfig().getRedstone().getEnable()&&!denied.isEmpty(),denied);
    cmd("eclean redstone on extra");check("toggle_rejects_extra_argument",!Config.INSTANCE.getConfig().getRedstone().getEnable(),"extra argument");
    cmd("eclean redstone on");Object old=RedstoneMonitor.INSTANCE.getWindow$EClean();cmd("eclean redstone on");check("toggle_repeated_on",old!=null&&RedstoneMonitor.INSTANCE.getWindow$EClean()!=null,"active");
    Config.INSTANCE.load(Bukkit.getConsoleSender());check("redstone_on_persisted",Config.INSTANCE.getConfig().getRedstone().getEnable(),"reload");cmd("eclean redstone off");check("redstone_off_stops_service",RedstoneMonitor.INSTANCE.getWindow$EClean()==null,"immediate");cmd("eclean redstone on");cmd("eclean redstone status");
  }
  String red(String mode,boolean enabled){return "redstone: {enable: "+enabled+", mode: "+mode+", window_ticks: 10, max_changes: 1, consecutive_windows: 2, cooldown_ticks: 40, ignored_worlds: [it_b]}\n";}
  void redstone(){
    for(World w:Arrays.asList(a,b)){for(int x=3;x<7;x++)w.getBlockAt(x,69,5).setType(Material.STONE);w.getBlockAt(5,70,5).setType(Material.REDSTONE_WIRE);}
    final boolean[] power={false};clock=Bukkit.getScheduler().runTaskTimer(this,()->{power[0]=!power[0];for(World w:Arrays.asList(a,b))w.getBlockAt(4,70,5).setType(power[0]?Material.REDSTONE_BLOCK:Material.AIR);},1,2);
    later(80,()->{
      check("physical_redstone_events",eventsA>0&&eventsB>0,eventsA+","+eventsB);check("report_does_not_suppress",suppressed==0&&!RedstoneMonitor.INSTANCE.getWindow$EClean().top("it_a").isEmpty(),suppressed);check("redstone_ignored_world",RedstoneMonitor.INSTANCE.getWindow$EClean().top("it_b").isEmpty(),eventsB);
      red=red("suppress",true);config();later(80,this::afterRedstone);
    });
  }
  void afterRedstone(){
    check("suppress_rising_only",suppressed>0&&falling>0,"suppressed="+suppressed+", falling="+falling);check("redstone_blocks_retained",a.getBlockAt(5,70,5).getType()==Material.REDSTONE_WIRE,"wire");clock.cancel();cmd("eclean redstone off");check("redstone_off_immediate",RedstoneMonitor.INSTANCE.getWindow$EClean()==null,"released");
    red=red("report",false);unload="chunk_unload: {enable: false, period_ticks: 5, idle_ticks: 40, keep_radius: 3, scan_budget: 4096, request_budget: 4, ignored_worlds: [world, world_nether, world_the_end, it_a, it_b]}\n";config();
    u.loadChunk(28,20);u.getBlockAt(449,70,321).setType(Material.STONE);u.save();u.getBlockAt(449,70,321).setType(Material.DIAMOND_BLOCK);
    u.loadChunk(24,20);force(u.getChunkAt(24,20),true);u.loadChunk(20,20);optional(u.getChunkAt(20,20),"addPluginChunkTicket",new Class[]{org.bukkit.plugin.Plugin.class},this);
    cmd("eclean chunkunload on");cmd("eclean chunkunload status");check("chunkunload_on_running",ChunkUnloader.INSTANCE.getPolicy$EClean()!=null,Compatibility.INSTANCE.getChunkProtection());
    later(15,()->check("idle_threshold_wait",ChunkUnloader.INSTANCE.getPolicy$EClean().getAttempted()==0,"no early request"));later(60,()->awaitUnload(60));
  }
  void awaitUnload(int elapsed){
    // Older servers begin with hundreds of spawn chunks; the request budget
    // deliberately limits progress. Wait for completion, bounded to 60 seconds.
    if((unloaded.contains("it_unload:28:20")&&!u.isChunkLoaded(28,20)&&ChunkUnloader.INSTANCE.getPolicy$EClean().getAccepted()>0)||elapsed>=1200){afterUnload();return;}
    later(20,()->awaitUnload(elapsed+20));
  }
  void afterUnload(){
    cmd("eclean unloadstats");long accepted=ChunkUnloader.INSTANCE.getPolicy$EClean().getAccepted();check("unload_request_accepted",accepted>0,accepted);
    boolean gone=unloaded.contains("it_unload:28:20")&&!u.isChunkLoaded(28,20);check("candidate_actually_unloaded",gone,"event plus isChunkLoaded=false");
    if(Compatibility.INSTANCE.getSupportsForceLoadedChunks())check("force_loaded_protected",u.isChunkLoaded(24,20)&&Compatibility.INSTANCE.isForceLoaded(u.getChunkAt(24,20)),"force flag");
    try{Chunk.class.getMethod("getPluginChunkTickets");check("plugin_ticket_protected",u.isChunkLoaded(20,20)&&Compatibility.INSTANCE.pluginTickets(u.getChunkAt(20,20))>0,"ticket");}catch(NoSuchMethodException e){getLogger().info("SKIP plugin_ticket_protected: API absent");}
    Material value=u.getBlockAt(449,70,321).getType();check("unsaved_block_persisted",gone&&value==Material.DIAMOND_BLOCK,value);
    cmd("eclean chunkunload off");check("chunkunload_off_stops_service",ChunkUnloader.INSTANCE.getPolicy$EClean()==null,"immediate");Config.INSTANCE.load(Bukkit.getConsoleSender());check("chunkunload_off_persisted",!Config.INSTANCE.getConfig().getChunkUnload().getEnable()&&ChunkUnloader.INSTANCE.getPolicy$EClean()==null,"reload");
    cmd("eclean worldrules off");cmd("eclean redstone on");cmd("eclean chunkunload on");u.save();write(checkpoint,"failures="+failures);getLogger().info("MATRIX_PHASE1_DONE failures="+failures);Bukkit.shutdown();
  }
  void restart(){
    // Resolve both classes before disabling their providing plugin/classloader.
    Object monitor=RedstoneMonitor.INSTANCE,unloader=ChunkUnloader.INSTANCE;
    check("restart_toggle_persistence",!Config.INSTANCE.getConfig().getWorldRules()&&RedstoneMonitor.INSTANCE.getWindow$EClean()!=null&&ChunkUnloader.INSTANCE.getPolicy$EClean()!=null,"world off; both services on");
    u=world("it_unload");u.loadChunk(28,20);force(u.getChunkAt(28,20),true);
    later(40,()->{
      Material value=u.getBlockAt(449,70,321).getType();check("restart_saved_block",value==Material.DIAMOND_BLOCK,value);
      Bukkit.getPluginManager().disablePlugin(Bukkit.getPluginManager().getPlugin("EClean"));check("disable_stops_services",RedstoneMonitor.INSTANCE.getWindow$EClean()==null&&ChunkUnloader.INSTANCE.getPolicy$EClean()==null,"released");
      later(5,()->{check("disable_cancels_tasks",Bukkit.getScheduler().getPendingTasks().stream().noneMatch(t->t.getOwner().getName().equals("EClean")),"no task");getLogger().info("MATRIX_RESTART_DONE failures="+failures);Bukkit.shutdown();});
    });
  }
  @EventHandler(priority=EventPriority.LOWEST) public void raw(BlockRedstoneEvent e){String n=e.getBlock().getWorld().getName();if(n.equals("it_a")||n.equals("it_b")){before.put(e,e.getNewCurrent());if(n.equals("it_a"))eventsA++;else eventsB++;}}
  @EventHandler(priority=EventPriority.MONITOR) public void end(BlockRedstoneEvent e){Integer raw=before.remove(e);if(raw==null||!e.getBlock().getWorld().getName().equals("it_a"))return;if(raw>e.getOldCurrent()&&e.getNewCurrent()==e.getOldCurrent())suppressed++;if(raw<e.getOldCurrent()&&e.getNewCurrent()==raw&&Config.INSTANCE.getConfig().getRedstone().getMode().equals("suppress"))falling++;}
  @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void unloaded(ChunkUnloadEvent e){unloaded.add(e.getWorld().getName()+":"+e.getChunk().getX()+":"+e.getChunk().getZ());}
}
