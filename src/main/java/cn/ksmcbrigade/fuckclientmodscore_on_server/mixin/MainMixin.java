package cn.ksmcbrigade.fuckclientmodscore_on_server.mixin;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.server.Main;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.eventbus.EventBus;
import net.minecraftforge.fml.*;
import net.minecraftforge.fml.config.ConfigTracker;
import net.minecraftforge.fml.javafmlmod.FMLModContainer;
import net.minecraftforge.fml.loading.FMLLoader;
import net.minecraftforge.fml.loading.LoadingModList;
import net.minecraftforge.fml.loading.moddiscovery.ModFile;
import net.minecraftforge.fml.loading.moddiscovery.ModFileInfo;
import net.minecraftforge.fml.loading.moddiscovery.ModInfo;
import net.minecraftforge.forgespi.language.IModInfo;
import net.minecraftforge.forgespi.locating.IModFile;
import net.minecraftforge.network.NetworkRegistry;
import org.apache.commons.io.FileUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

@Mixin(Main.class)
public class MainMixin {

    @Unique
    private static File config = new File("fcm-config.json");
    @Unique
    private static File other_config = new File("fcm-adv-config.json");
    @Unique
    private static ArrayList<String> data = null;
    @Unique
    private static ArrayList<ModFileInfo> modFileInfos = new ArrayList<>();
    @Unique
    private static ArrayList<ModContainer> allModContainers = new ArrayList<>();
    @Unique
    private static Map<String,ModContainer> indexModsMap = new HashMap<>();

    @Inject(method = "main",at = @At(value = "INVOKE", target = "Lnet/minecraft/server/Bootstrap;validate()V",shift = At.Shift.BEFORE))
    private static void main(String[] p_129699_, CallbackInfo ci) throws NoSuchFieldException, IllegalAccessException, NoSuchMethodException, InvocationTargetException {
        ModLoader loader = ModLoader.get();
        Field field = loader.getClass().getDeclaredField("loadingModList");
        Field indexMods = ModList.class.getDeclaredField("indexedMods");
        Field configs = ConfigTracker.class.getDeclaredField("fileMap");
        Field networkConfigs = NetworkRegistry.class.getDeclaredField("instances");
        Field listeners = EventBus.class.getDeclaredField("listeners");
        field.setAccessible(true);
        listeners.setAccessible(true);
        networkConfigs.setAccessible(true);
        configs.setAccessible(true);
        indexMods.setAccessible(true);
        Method buildMod = loader.getClass().getDeclaredMethod("buildMods", IModFile.class);
        Method setLoad = ModList.class.getDeclaredMethod("setLoadedMods", List.class);
        setLoad.setAccessible(true);
        buildMod.setAccessible(true);
        LoadingModList loadingModList = (LoadingModList) field.get(loader);
        boolean need = need();
        for (ModFileInfo modFile : loadingModList.getModFiles()) {
            allModContainers.addAll((List<ModContainer>) buildMod.invoke(loader,modFile.getFile()));
        }
        for (ModContainer allModContainer : allModContainers) {
            indexModsMap.put(allModContainer.getModId(),allModContainer);
        }
        for (ModFileInfo modFile : loadingModList.getModFiles()) {
            boolean r = false;
            for (IModInfo mod : modFile.getMods()) {
                if(mod.getModId().equalsIgnoreCase("forge") || mod.getModId().equalsIgnoreCase("minecraft") || skip(mod)){
                    r = true;
                    System.out.println("Skipped a mod scan "+modFile.getFile().getFilePath());
                    break;
                }
            }
            if(r) continue;
            boolean sp = false,b = false;
            for (IModInfo mod : modFile.getMods()) {
                sp = false;
                if(mod.getDependencies()==null) continue;
                for (IModInfo.ModVersion dependency : mod.getDependencies()) {
                    if(dependency.getSide().equals(IModInfo.DependencySide.CLIENT)){
                        loadingModList = remove(loadingModList,modFile);
                        sp = true;
                        disabled(modFile);
                        break;
                    }
                }
            }
            for (IModInfo mod : modFile.getMods()) {
                if(black(mod.getModId())){
                    b = true;
                    break;
                }
            }
            if(b){
                loadingModList = remove(loadingModList,modFile);
                disabled(modFile);
                continue;
            }
            if(sp) continue;
            if(need){
                try {
                    fuckclientmodscore$getModList(loadingModList);
                    setLoad.invoke(ModList.get(),allModContainers);
                    indexMods.set(ModList.get(),indexModsMap);
                    System.out.println("Testing "+modFile.getFile());
                    List<ModContainer> mods = (List<ModContainer>) buildMod.invoke(loader,modFile.getFile());
                    for (ModContainer mod : mods) {
                        if(mod.getClass().getName().startsWith("net.minecraftforge.fml.ModLoader.ErroredModContainer")){
                            throw new RuntimeException("Catch it!");
                        }
                        if(mod instanceof FMLModContainer fmlModContainer){
                            ModLoadingContext.get().setActiveContainer(mod);
                            configs.set(ConfigTracker.INSTANCE,new ConcurrentHashMap<>());
                            networkConfigs.set(null,Collections.synchronizedMap(new HashMap<>()));
                            MinecraftForge.EVENT_BUS.shutdown();
                            listeners.set(MinecraftForge.EVENT_BUS,new ConcurrentHashMap<>());
                            Method con = fmlModContainer.getClass().getDeclaredMethod("constructMod");
                            con.setAccessible(true);
                            con.invoke(fmlModContainer);
                        }
                    }
                } catch (Throwable e) {
                    String del = get(e);
                    if(e instanceof ClassNotFoundException || e instanceof NoClassDefFoundError ||
                            del.contains("Attempted to load class") || del.contains("NoClassDefFoundError") || del.contains("ClassNotFoundException")){
                        loadingModList = remove(loadingModList,modFile);
                        disabled(modFile);
                        continue;
                    }
                }
                continue;
            }
            try (JarFile file = new JarFile(modFile.getFile().getFilePath().toFile())){
                Enumeration<JarEntry> entryEnumeration = file.entries();
                while (entryEnumeration.hasMoreElements()){
                    JarEntry entry = entryEnumeration.nextElement();
                    if(entry.getName().endsWith(".class")){
                        String clazz = entry.getName().replace("/",".").replace(".class","");
                        try {
                            //Class<?> clzza = Class.forName(clazz,false,MainMixin.class.getClassLoader());
                            /*if(need && JarAnnotationChecker.hasAnnotation(file,entry,"net.minecraftforge.fml.common.Mod")){
                                Class<?> clazzC = Class.forName(clazz,true,MainMixin.class.getClassLoader());
                                System.out.println(clazzC.getConstructor().newInstance().getClass().getName());
                                System.out.println("!!!!!!"+clazz);
                            }
                            else if(need &&
                                    JarAnnotationChecker.hasAnnotation(file,entry,"net.minecraftforge.fml.common.Mod.EventBusSubscriber")
                                    && JarAnnotationChecker.referencesMinecraftClient(file,entry)){
                                System.out.println("6666666"+clazz);
                                loadingModList = remove(loadingModList,modFile);
                                new Thread(()->{
                                    if(modFileInfos.contains(modFile)) return;
                                    modFileInfos.add(modFile);
                                    while (!modFile.getFile().getFilePath().toFile().renameTo(new File(modFile.getFile().getFilePath().toString()+".disabled"))){
                                        Thread.yield();
                                    }
                                    System.out.println("Disabled a mod "+modFile.getFile().getFilePath());
                                }).start();
                                continue;
                            }*/
                            if(!need){
                                Class.forName(clazz,true,MainMixin.class.getClassLoader());
                            }
                        } catch (Throwable e) {
                            String del = get(e).toLowerCase();
                            if(clazz.startsWith("net.minecraft") || clazz.startsWith("com.mojang") ||
                                    (clazz.toLowerCase().contains("client") && !clazz.toLowerCase().contains("server")) ||
                                    del.contains("net.minecraft.client") || del.contains("net/minecraft/client")
                                    || del.contains("com.mojang") || del.contains("del/mojang") || (del.contains("client") && !del.contains("server"))){
                                loadingModList = remove(loadingModList,modFile);
                                    disabled(modFile);
                                continue;
                            }
                        }
                    }
                }
            }
            catch (Exception e){
                System.out.println("Error in scan mod files.");
                e.printStackTrace();
            }
        }
        field.set(loader,loadingModList);
        System.out.println("All mods: ");
        for (ModInfo mod : loadingModList.getMods()) {
            System.out.println(mod.getModId());
        }
        configs.set(ConfigTracker.INSTANCE,new ConcurrentHashMap<>());
        networkConfigs.set(null,Collections.synchronizedMap(new HashMap<>()));
        listeners.set(MinecraftForge.EVENT_BUS,new ConcurrentHashMap<>());
        MinecraftForge.EVENT_BUS.start();
    }

    @Unique
    private static void fuckclientmodscore$getModList(LoadingModList loadingModList) {
        ModList.of(loadingModList.getModFiles().stream().map(ModFileInfo::getFile).toList(),loadingModList.getMods());
    }
    @Unique
    private static void disabled(ModFileInfo modFile){
        new Thread(()->{
            if(modFileInfos.contains(modFile)) return;
            modFileInfos.add(modFile);
            while (!modFile.getFile().getFilePath().toFile().renameTo(new File(modFile.getFile().getFilePath().toString()+".disabled"))){
                Thread.yield();
            }
            System.out.println("Disabled a mod "+modFile.getFile().getFilePath());
        }).start();
    }

    @Unique
    private static LoadingModList remove(LoadingModList list, ModFileInfo modFileInfo){
        ArrayList<ModFile> modFiles = new ArrayList<>();
        ArrayList<ModInfo> modInfos = new ArrayList<>();
        for (ModFileInfo modFile : list.getModFiles()) {
            if(modFile!=modFileInfo){
                modFiles.add(modFile.getFile());
            }
        }
        for (ModInfo mod : list.getMods()) {
            if(mod.getOwningFile()!=modFileInfo){
                modInfos.add(mod);
            }
        }
        return LoadingModList.of(modFiles,modInfos,null);
    }

    @Unique
    private static String get(Throwable throwable){
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        throwable.printStackTrace(pw);
        return sw.toString();
    }

    @Unique
    private static boolean skip(IModInfo modInfo) {
        try {
            if(!config.exists()){
                JsonArray array = new JsonArray();
                array.add("jei");
                array.add("ftbultimine");
                array.add("xaerominimap");
                array.add("xaerominimap_core");
                array.add("xaeroworldmap");
                array.add("xaeroworldmap_core");
                array.add("veniminer");
                array.add("VeinMinerModSupport");
                array.add("lod");
                array.add("distanthorizons");
                array.add("twilightforest");
                FileUtils.writeStringToFile(config,array.toString());
            }
            if(config.exists()){
                if(data==null){
                    data = new ArrayList<>();
                    JsonArray array = JsonParser.parseString(FileUtils.readFileToString(config)).getAsJsonArray();
                    for (JsonElement jsonElement : array) {
                        data.add(jsonElement.getAsString());
                    }
                }
                for (String datum : data) {
                    if(datum.equals(modInfo.getModId())){
                        return true;
                    }
                }
            }
            return false;
        } catch (Exception e) {
            System.out.println("Error in parse the config.");
            e.printStackTrace();
            return false;
        }
    }


    @Unique
    private static boolean need() {
        try {
            if(!other_config.exists()){
                JsonObject object = new JsonObject();
                object.addProperty("modCreate",true);
                object.add("blacks",new JsonArray());
                FileUtils.writeStringToFile(other_config,object.toString());
            }
            if(other_config.exists()){
                JsonObject object = JsonParser.parseString(FileUtils.readFileToString(other_config)).getAsJsonObject();
                return object.get("modCreate").getAsBoolean();
            }
            return false;
        } catch (Exception e) {
            System.out.println("Error in parse the config.");
            e.printStackTrace();
            return false;
        }
    }

    @Unique
    private static boolean black(String modId) {
        try {
            if(!other_config.exists()){
                JsonObject object = new JsonObject();
                object.addProperty("modCreate",true);
                object.add("blacks",new JsonArray());
                FileUtils.writeStringToFile(other_config,object.toString());
            }
            if(other_config.exists()){
                JsonArray object = JsonParser.parseString(FileUtils.readFileToString(other_config)).getAsJsonObject().getAsJsonArray("blacks");
                for (JsonElement jsonElement : object) {
                    if(modId.equalsIgnoreCase(jsonElement.getAsString())) return true;
                }
            }
            return false;
        } catch (Exception e) {
            System.out.println("Error in parse the config.");
            e.printStackTrace();
            return false;
        }
    }
}
