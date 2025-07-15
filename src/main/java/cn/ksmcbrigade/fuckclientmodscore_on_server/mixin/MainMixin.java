package cn.ksmcbrigade.fuckclientmodscore_on_server.mixin;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import net.minecraft.server.Main;
import net.minecraftforge.fml.ModLoader;
import net.minecraftforge.fml.loading.LoadingModList;
import net.minecraftforge.fml.loading.moddiscovery.ModFile;
import net.minecraftforge.fml.loading.moddiscovery.ModFileInfo;
import net.minecraftforge.fml.loading.moddiscovery.ModInfo;
import net.minecraftforge.forgespi.language.IModInfo;
import org.apache.commons.io.FileUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.File;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

@Mixin(Main.class)
public class MainMixin {

    @Unique
    private static File config = new File("fcm-config.json");
    @Unique
    private static ArrayList<String> data = null;

    @Inject(method = "main",at = @At(value = "INVOKE", target = "Lnet/minecraft/server/Bootstrap;validate()V",shift = At.Shift.BEFORE))
    private static void main(String[] p_129699_, CallbackInfo ci) throws NoSuchFieldException, IllegalAccessException {
        ModLoader loader = ModLoader.get();
        Field field = loader.getClass().getDeclaredField("loadingModList");
        field.setAccessible(true);
        LoadingModList loadingModList = (LoadingModList) field.get(loader);
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
            try (JarFile file = new JarFile(modFile.getFile().getFilePath().toFile())){
                Enumeration<JarEntry> entryEnumeration = file.entries();
                while (entryEnumeration.hasMoreElements()){
                    JarEntry entry = entryEnumeration.nextElement();
                    if(entry.getName().endsWith(".class")){
                        String clazz = entry.getName().replace("/",".").replace(".class","");
                        try {
                            Class.forName(clazz,true,MainMixin.class.getClassLoader());
                        } catch (Throwable e) {
                            String del = get(e).toLowerCase();
                            if(clazz.startsWith("net.minecraft") || clazz.startsWith("com.mojang") ||
                                    (clazz.toLowerCase().contains("client") && !clazz.toLowerCase().contains("server")) ||
                                    del.contains("net.minecraft.client") || del.contains("net/minecraft/client")
                                    || del.contains("com.mojang") || del.contains("del/mojang") || (del.contains("client") && !del.contains("server"))){
                                loadingModList = remove(loadingModList,modFile);
                                new Thread(()->{
                                    //System.out.println("Disabling "+modFile.getFile().getFilePath());
                                    while (!modFile.getFile().getFilePath().toFile().renameTo(new File(modFile.getFile().getFilePath().toString()+".disabled"))){
                                        Thread.yield();
                                    }
                                    //System.out.println("Disabled a mod "+modFile.getFile().getFilePath());
                                }).start();
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
        StringBuilder builder = new StringBuilder();
        builder.append(throwable.toString()).append("\n");
        if(throwable.getLocalizedMessage()!=null) builder.append(throwable.getLocalizedMessage()).append("\n");
        if(throwable.getCause()!=null) builder.append(throwable.getCause().toString()).append("\n");
        if(throwable.getStackTrace()!=null){
            for (StackTraceElement stackTraceElement : throwable.getStackTrace()) {
                builder.append(stackTraceElement.toString()).append("\n");
            }
        }
        if(throwable.getSuppressed()!=null){
            for (Throwable stackTraceElement : throwable.getSuppressed()) {
                builder.append(stackTraceElement.toString()).append("\n");
            }
        }
        return builder.toString();
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
}
