package com.example.fishy.feature.home

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.fishy.ui.components.ColumnScrollIndicator
import com.example.fishy.ui.components.FishyButton
import com.example.fishy.ui.theme.FishyAccent
import com.example.fishy.ui.theme.FishyAccentLink
import com.example.fishy.ui.theme.isLightTheme

/** Russian-only easter egg text; not localized. */
internal const val EASTER_EGG_ALE_LINE = "Дюжина добрых бочонков хмельного эля"

/** Closing runes line — shown after the saga body. */
internal const val EASTER_EGG_RUNES = "ᚢ ᚱ ᛞ ᚦ ᛋ ᛝ ᛏ ᛉ ᛟ ᚹ"

internal val LOGO_EASTER_MESSAGES = listOf(
    "Something is fishy here...",
    "Сечёшь фишку?",
    "ПОЛУНДРА!!! А, нет, показалось.",
    "Вот это погодка, якорь мне в жабры!",
    "Буль-буль. И этим всё сказано.",
    "Перекурим, брат?"
)

internal val EASTER_EGG_SAGA = """
Громче бури над Златорогой крепостью гремит глас славных седобородых владык-купцов, чьих имен много, как волн в море! Несётся воззвание: «О, хранитель пути! Пробудись же! Наполни телеги железны морскими чудовищами, что скованы хладом, в той гавани, где велика ладья стоит. Да будет в каждом возе обильно улова!». Как Фенрир, что в цепях исполнен терпения и мерит своими вздохами мгновения до часа пира, так и за многими горами иные могучие конунги во граде белокаменном, что лежит на семи холмах у быстрых вод, очей не смыкают, ожидая суженой добычи прибытья.

Тогда я, хранитель пути, их решимость завидев, искренне возжелав исполнить долг свой, клянусь свершить великие дела, дабы ничто не остановило меня, как Хель не может остановить того, кто ещё не переступил порога её владений, и клятвы мои звучат громко: Высечь обережный свиток — весть гавани ярлу: сеча грядёт! Слово моё, священною силой печати владык-купцов скрепленное, вернее Гунгнира пронзит все преграды, не ведая промаха, ибо Одина копьё одного разит, а слово кормит тысячи. Не в униженьи преклонив колени, но честным серебром суждено мне дань отмерить на право прохода обоза во ярловы владенья, где каменны замки хлад на скалах прибрежных хранят. Проницаньем своим впору мне пресечь кривотолки об умыслах лютых, вручив стражам привратным грамоту с именами тех мужей, коим нужда есть в гавань въехать. Ибо кто платит сполна и ведёт счёт открыто — будто тис, стоек в земле и крепок. Кликнуть гонцов! Пусть тревожат скорых возниц седлать колесницы громом ревущие, пламенем ведомые – пора везти ледяные телеги, хладом своим стерегущие рыбье племя! Десятник, созывай мужей тягловых – идёт наша рать!

На берегу туманном края мира, где краб таится средь камней, а моря рог вонзился в сушу, на солнце златом отливая, – там поступь моя твердостью преисполнена, с какой Тюр вложил длань свою в пасть ужасного волка – наступает час битвы! Из хладных океанов, Эгировых вотчин, где Фрейра светило не смеет греть тёмных пучин, явился драккар ледяной. И имя ему — Кит из Нифльхейма, и в чреве его покоятся сельди, что не ведают тленья, покуда держит он путь, рассекая солёные воды краёв, где севера мрак встречает рассвет. Едва он швартуется у каменных зубцов причала, ревёт стальной Йотун, исполином нерушимым в скалу вросший да хребет над фиордами гнущий, и к ладье простираются длани его. Ухватив подносы, из древа сложенные, берестяных сундуков полные, он переносит их с утробы драккара на твердь не медля, ибо тяжек его нрав, но праведен труд. Дюжие мужи, кованых быков оседлавшие, молнией движимых, рогами их, что два булатных меча, споро втискивают ту ношу в пасть воза. Я, словно Один, что зорок стал ценою ока, считаю каждый сундук, рыбинами набитый: да будет в каждой телеге ровно столько, сколько владыки-купцы сочли достойным! Дабы не увезти чужого добра и не уронить славу имени своего, чутко слежу за рунами, что храбрые моряки начертали на сундуках. Ладен мой труд: с рыбин срываю покровы берестяные, гляжу – хладна ли, велика ли, добротна ли? А коли воз полон, опечатываю его на путь узлом-оберегом защитным, да свитки вручаю лихому вознице. И пусть даже та валькирия-страж, что Асгардом ставлена во имя меры правды, не найдёт в нашем замысле кривды, ибо нет от неё прощения, а строгость её страшна, как ярость Тора, когда заносит он Мьёльнир!

Лишь после того, как обоз отбыл к знатным конунгам в стольный град, пишу я послание о том, каков улов в нём покоится да сколь он велик. И вручаю сию благую весть достойным мужам, дабы ведали они цену моего подвига. Так кузница та, что в центре мира за хребтами Рифейскими денно и нощно горнилом гремит, морским чудовищам в кипящей пучине чешую на железные латы меняя, утоляет свой голод — словно Фенрир, разжавший пасть. Но ныне цепи вновь волка того сковали терпением — до следующей сечи в тех краях, куда первым приходит утро.

Мудрый же ведает: иная участь ждёт беспечных. Горе ж тем хранителям пути, кто слаб умом и волей вял, кто навлекает гнев седобородых конунгов и плач далёких кузниц, тишиной томимых — ибо возы, снедью пустые, и серебро, воды прозрачней сквозь пальцы утёкшее, иному бывают горше меча супостата. Имена недостойных забудутся во тьмах, и не будет места им ни в ладье, ни у очага, ибо кто теряет добычу — теряет и честь, и доброе имя.

Славный же будет пир там, где солнце свершает свой путь! И столы прогнутся от даров Эгира! И Ньёрд благословит верных делу! И не скоро смолкнут скальды, воспевая мудрость владык-купцов! Да будет имя хранителя пути помянуто на пиру, покуда течёт мёд!
""".trimIndent()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EasterEggScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Сага о Хранителе Пути") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                }
            )
        }
    ) { padding ->
        val runesColor = if (isLightTheme()) FishyAccentLink else FishyAccent
        val eggScroll = rememberScrollState()
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(end = 8.dp)
                    .padding(horizontal = 20.dp, vertical = 12.dp)
                    .verticalScroll(eggScroll)
            ) {
                Text(
                    text = EASTER_EGG_SAGA,
                    textAlign = TextAlign.Start
                )
                Spacer(modifier = Modifier.height(28.dp))
                ShimmerRunesText(
                    text = EASTER_EGG_RUNES,
                    color = runesColor,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(28.dp))
                FishyButton(
                    onClick = onBack,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "SKÅL!!!",
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
            ColumnScrollIndicator(
                scrollState = eggScroll,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .padding(vertical = 4.dp)
            )
        }
    }
}

/** Looping light band across the closing turquoise runes. */
@Composable
private fun ShimmerRunesText(
    text: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    var textWidthPx by remember { mutableFloatStateOf(0f) }
    val transition = rememberInfiniteTransition(label = "easterRunesShimmer")
    val t by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "easterRunesShimmerT"
    )
    val brush = remember(t, textWidthPx, color) {
        if (textWidthPx <= 0f) {
            SolidColor(color)
        } else {
            val band = textWidthPx * 0.45f
            val startX = -band + (textWidthPx + band) * t
            Brush.linearGradient(
                colors = listOf(
                    color,
                    Color.White.copy(alpha = 0.92f),
                    color
                ),
                start = Offset(startX, 0f),
                end = Offset(startX + band, 0f)
            )
        }
    }
    Text(
        text = text,
        style = TextStyle(
            brush = brush,
            fontSize = 34.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            lineHeight = 42.sp
        ),
        modifier = modifier.onSizeChanged { textWidthPx = it.width.toFloat() }
    )
}
