package cn.ksmcbrigade.fuckclientmodscore_on_server;

import net.minecraftforge.fml.common.Mod;

import java.io.PrintWriter;
import java.io.StringWriter;


@Mod(Fuckclientmodscore.MODID)
public class Fuckclientmodscore {
    public static final String MODID = "fuckclientmodscore";

    public Fuckclientmodscore(){
        System.out.println("Hello Fuck client mods core mod!");
    }

    private static String get(Throwable throwable){
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        throwable.printStackTrace(pw);
        return sw.toString();
    }
}
