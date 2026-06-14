package com.kharagedition.tibetankeyboard.ui.about

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kharagedition.tibetankeyboard.R
import com.kharagedition.tibetankeyboard.ui.compose.components.AppIcons
import com.kharagedition.tibetankeyboard.ui.compose.components.BackHeader
import com.kharagedition.tibetankeyboard.ui.compose.components.BoText
import com.kharagedition.tibetankeyboard.ui.compose.components.ScreenScaffold
import com.kharagedition.tibetankeyboard.ui.compose.theme.TibetanColors
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource

class AboutActions(
    val onBack: () -> Unit,
    val onShare: () -> Unit,
    val onRate: () -> Unit,
    val onDictionary: () -> Unit,
    val onCalendar: () -> Unit,
    val onGithub: () -> Unit,
    val onGmail: () -> Unit,
)

@Composable
fun AboutScreen(actions: AboutActions) {
    ScreenScaffold {
        BackHeader(stringResource(R.string.action_about), onBack = actions.onBack)

        Column(Modifier.padding(horizontal = 18.dp)) {
            // hero cover
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(TibetanColors.Brown400, TibetanColors.Brown600, TibetanColors.Bg800)
                        )
                    )
                    .border(1.dp, TibetanColors.Line, RoundedCornerShape(20.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Image(
                        painter = painterResource(R.mipmap.ic_launcher),
                        contentDescription = null,
                        modifier = Modifier.size(72.dp,).clip(RoundedCornerShape(10.dp))
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.app_name), color = TibetanColors.Cream, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }

            // about card
            Spacer(Modifier.height(16.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(TibetanColors.Brown700)
                    .border(1.dp, TibetanColors.Line, RoundedCornerShape(18.dp))
                    .padding(18.dp)
            ) {
                Text(stringResource(R.string.about_this_app), color = TibetanColors.Cream, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.height(12.dp))
                listOf(
                    "A simple, friendly way to type in Tibetan.",
                    "Built and maintained as open source.",
                    "Your feedback and rating mean a lot to us.",
                ).forEach { line ->
                    Row(modifier = Modifier.padding(bottom = 11.dp), horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                        Box(
                            modifier = Modifier.padding(top = 7.dp).size(6.dp).clip(RoundedCornerShape(99.dp)).background(TibetanColors.Gold400)
                        )
                        Text(line, color = TibetanColors.Cream2, fontSize = 14.sp, lineHeight = 21.sp)
                    }
                }
                Spacer(Modifier.height(3.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PillAction("Share", AppIcons.Share, Modifier.weight(1f), actions.onShare)
                    PillAction("Rate", AppIcons.Star, Modifier.weight(1f), actions.onRate)
                }
            }

            // more from KharagEdition
            Spacer(Modifier.height(22.dp))
            Text(
                stringResource(R.string.more_from_kharag),
                color = TibetanColors.CreamDim, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp,
                modifier = Modifier.padding(start = 2.dp, bottom = 12.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SisterApp("Tb–En Dictionary", "ཚིག་མཛོད", R.drawable.dictionary,Modifier.weight(1f), actions.onDictionary)
                SisterApp("Tibetan Calendar", "ལོ་ཐོ", R.drawable.calendar,Modifier.weight(1f), actions.onCalendar)
            }

            // support
            Spacer(Modifier.height(22.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                PillAction("GitHub", Icons.Rounded.Code, Modifier.weight(1f), actions.onGithub)
                PillAction("Contact", Icons.Rounded.Email, Modifier.weight(1f), actions.onGmail)
            }

            Spacer(Modifier.height(20.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Made with ", color = TibetanColors.CreamFaint, fontSize = 12.sp)
                Icon(Icons.Rounded.Favorite, null, tint = TibetanColors.MaroonSoft, modifier = Modifier.size(13.dp))
                Text(" by KharagEdition", color = TibetanColors.CreamFaint, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun PillAction(label: String, icon: ImageVector, modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(TibetanColors.Brown600)
            .clickable(onClick = onClick)
            .padding(vertical = 11.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = TibetanColors.Gold300, modifier = Modifier.size(17.dp))
        Spacer(Modifier.size(8.dp))
        Text(label, color = TibetanColors.Cream, fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SisterApp(title: String, bo: String,drawableId:Int, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(TibetanColors.Brown700)
            .border(1.dp, TibetanColors.Line, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(
                    Brush.linearGradient(
                        listOf(
                            TibetanColors.Brown500,
                            TibetanColors.Brown600
                        )
                    )
                ),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(drawableId),
                contentDescription = null,
                modifier = Modifier.size(42.dp,).clip(RoundedCornerShape(10.dp))
            )
        }
        Text(title, color = TibetanColors.Cream, fontSize = 13.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(AppIcons.Download, null, tint = TibetanColors.Gold300, modifier = Modifier.size(15.dp))
            Text("Get", color = TibetanColors.Gold300, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
        }
    }
}
