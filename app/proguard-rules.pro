# Custom views are inflated from XML, so keep their constructors.
-keep public class io.github.figs0203.afinador.ui.** extends android.view.View {
    public <init>(android.content.Context, android.util.AttributeSet);
}
