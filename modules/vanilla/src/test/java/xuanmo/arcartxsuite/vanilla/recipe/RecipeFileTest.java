package xuanmo.arcartxsuite.vanilla.recipe;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.nio.file.Path;
import java.util.*;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import xuanmo.arcartxsuite.api.capability.VanillaRecipeAccess.*;
import xuanmo.arcartxsuite.api.item.ItemSourceRegistry;

class RecipeFileTest {
    @TempDir Path directory;
    @Test void editorSavePreservesReadableItemLibraryReferencesAndNbt() throws Exception {
        var items=mock(ItemSourceRegistry.class);
        when(items.generateItem(eq("minecraft"),eq("DIAMOND"),anyInt(),eq("{CustomModelData:173}"),isNull()))
            .thenAnswer(call -> new ItemStack(Material.DIAMOND,call.getArgument(2)));
        var factory=mock(ItemFactory.class);
        when(factory.equals(null,null)).thenReturn(true);
        var unsafe=mock(UnsafeValues.class);
        when(unsafe.getDataVersion()).thenReturn(3465);
        try(var bukkit=mockStatic(Bukkit.class)){
            bukkit.when(Bukkit::getItemFactory).thenReturn(factory);
            bukkit.when(Bukkit::getUnsafe).thenReturn(unsafe);
            var definition=new Definition("library",Station.SHAPED,List.of(
                new Ingredient(List.of(),List.of(new ItemStack(Material.DIAMOND)))),
                new ItemStack(Material.DIAMOND,4),"",200,0,SmithingData.INHERIT_BASE);
            Map<String,Object> reference=Map.of("source","minecraft","id","DIAMOND","amount",1,"nbt","{CustomModelData:173}");
            var yaml=new YamlConfiguration();
            yaml.createSection("recipes.library",RecipeFile.serialize(definition,Map.of(0,reference,9,reference)));
            var file=new RecipeFile(directory.resolve("recipes.yml"),items);
            file.write(yaml);
            var document=file.read();
            assertEquals(4,document.definitions().get("library").result().getAmount());
            var references=RecipeFile.references(document,"library");
            assertEquals("minecraft",references.get(0).get("source"));
            assertEquals("{CustomModelData:173}",references.get(0).get("nbt"));
            assertEquals(4,references.get(9).get("amount"));
        }
    }
}
