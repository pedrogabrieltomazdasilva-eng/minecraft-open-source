package org.openmc.server

import android.app.Activity
import android.app.AlertDialog
import android.app.ActivityManager
import android.content.Intent
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.Gravity
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import org.openmc.server.UiKit.add
import org.openmc.server.UiKit.dp

class MainActivity : Activity() {
    private val runtime by lazy { AndroidRuntime(this) }
    private val profiles by lazy { ServerProfiles(this) }
    private val process get() = ServerSession.process
    private lateinit var root: LinearLayout
    private lateinit var body: LinearLayout
    private var page = Page.HOME
    private var tab = Tab.CONSOLE
    private var selected: ServerProfile? = null
    private var editing: ServerProfile? = null
    private var step = 0
    private var name = ""
    private var description = ""
    private var identifier = ""
    private var software = SOFTWARE.first()
    private var version = ""
    private var catalog: MinecraftCatalog.Catalog? = null
    private var catalogStatus = "Catálogo ainda não consultado."
    private var ram = 1024
    private var difficulty = "normal"
    private var gameMode = "survival"
    private var auth = true
    private var worldType = "normal"
    private var lanEnabled = true
    private var tcpPort = 25565
    private var udpPort = 19132
    private var geyserRequested = false
    private var floodgateRequested = false
    private var eula = false
    private var viewedFolder: File? = null
    private var importFolder: File? = null
    private var importingJar = false
    private var statusView: TextView? = null
    private var logView: TextView? = null
    private var startView: TextView? = null
    private var stopView: TextView? = null
    private var testView: TextView? = null
    private var sendView: TextView? = null
    private var commandView: EditText? = null
    private val listener: () -> Unit = { runOnUiThread { updateLive() } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = UiKit.background
        window.navigationBarColor = UiKit.background
        render()
    }
    override fun onStart() { super.onStart(); ServerSession.attach(listener) }
    override fun onStop() { ServerSession.detach(listener); super.onStop() }

    private fun render() {
        statusView = null; logView = null; startView = null; stopView = null
        testView = null; sendView = null; commandView = null
        root = UiKit.column(this).apply { setBackgroundColor(UiKit.background) }
        val header = UiKit.column(this, 20)
        header.add(UiKit.text(this, "MINECRAFT OPEN SERVER", 11f, UiKit.emerald, true))
        header.add(UiKit.title(this, when (page) {
            Page.HOME -> "Início"
            Page.SERVERS -> "Servidores"
            Page.FILES -> "Arquivos"
            Page.SETTINGS -> "Configurações"
            Page.WIZARD -> if (editing == null) "Novo servidor" else "Editar servidor"
            Page.DETAIL -> selected?.name ?: "Servidor"
            Page.DIAGNOSTICS -> "Diagnóstico"
        }), 4)
        if (page in listOf(Page.WIZARD, Page.DETAIL, Page.DIAGNOSTICS))
            header.add(UiKit.button(this, "←  Voltar") { goBack() }, 10)
        root.addView(header)
        val scroll = ScrollView(this).apply { isFillViewport = true; isVerticalScrollBarEnabled = false }
        body = UiKit.column(this).apply { setPadding(dp(20), dp(2), dp(20), dp(28)) }
        scroll.addView(body)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        when (page) {
            Page.HOME -> home()
            Page.SERVERS -> servers()
            Page.FILES -> globalFiles()
            Page.SETTINGS -> globalSettings()
            Page.WIZARD -> wizard()
            Page.DETAIL -> detail()
            Page.DIAGNOSTICS -> diagnostics()
        }
        if (page != Page.WIZARD) bottomNavigation()
        root.alpha = 0f
        setContentView(root)
        root.animate().alpha(1f).setDuration(160L).start()
        updateLive()
    }

    private fun bottomNavigation() {
        val row = UiKit.row(this).apply {
            setPadding(dp(6), dp(7), dp(6), dp(7))
            background = UiKit.navigationBackground(this@MainActivity)
        }
        listOf(
            Triple("Início", Page.HOME, R.drawable.ic_servers),
            Triple("Servidores", Page.SERVERS, R.drawable.ic_servers),
            Triple("Criar", Page.WIZARD, R.drawable.ic_add),
            Triple("Arquivos", Page.FILES, R.drawable.ic_folder),
            Triple("Ajustes", Page.SETTINGS, R.drawable.ic_settings)
        ).forEach { (title, target, icon) ->
            val item = UiKit.text(this, title, 11f, if (page == target) UiKit.emerald else UiKit.muted, true).apply {
                gravity = Gravity.CENTER; minHeight = dp(54); isClickable = true
                val picture = getDrawable(icon)?.mutate()
                picture?.setTint(if (page == target) UiKit.emerald else UiKit.muted)
                setCompoundDrawablesWithIntrinsicBounds(null, picture, null, null)
                compoundDrawablePadding = dp(3)
                setOnClickListener { if (target == Page.WIZARD) openWizard() else { page = target; render() } }
            }
            row.addView(item, LinearLayout.LayoutParams(0, -2, 1f))
        }
        root.addView(row, LinearLayout.LayoutParams(-1, -2).apply {
            setMargins(dp(10), dp(3), dp(10), dp(10))
        })
    }

    private fun home() {
        body.add(UiKit.text(this, "Seu espaço para criar e acompanhar servidores Java no celular.", 14f, UiKit.muted), bottom = 16)
        body.add(UiKit.card(this).apply {
            add(UiKit.text(this@MainActivity, "COMECE POR AQUI", 11f, UiKit.emerald, true))
            add(UiKit.text(this@MainActivity, "Crie seu servidor", 22f, UiKit.white, true), 8)
            add(UiKit.text(this@MainActivity, "O assistente organiza as escolhas. Você importa o JAR do servidor antes de iniciar.", 13f, UiKit.muted), 7)
            add(UiKit.button(this@MainActivity, "Criar servidor", true, R.drawable.ic_add) { openWizard() }, 18)
        })
        body.add(UiKit.card(this).apply {
            add(UiKit.text(this@MainActivity, "Runtime Android", 17f, UiKit.white, true))
            add(UiKit.text(this@MainActivity, "OpenJDK 25 ARM64 em diagnóstico. A inicialização ainda requer um build corrigido.", 13f, UiKit.muted), 7)
            add(UiKit.button(this@MainActivity, "Abrir diagnósticos", icon = R.drawable.ic_diagnostics) { page = Page.DIAGNOSTICS; render() }, 14)
        }, 12)
        serverCards(limit = 2)
    }

    private fun servers() {
        body.add(UiKit.text(this, "Perfis locais e estado observado dos processos.", 14f, UiKit.muted), bottom = 16)
        body.add(UiKit.button(this, "Novo servidor", true, R.drawable.ic_add) { openWizard() }, bottom = 18)
        serverCards()
    }

    private fun serverCards(limit: Int = Int.MAX_VALUE) {
        val servers = profiles.all()
        body.add(UiKit.text(this, "SERVIDORES  ·  ${servers.size}", 12f, UiKit.muted, true), 16, 10)
        if (servers.isEmpty()) body.add(UiKit.card(this).apply {
            add(UiKit.text(this@MainActivity, "Nenhum servidor ainda", 17f, UiKit.white, true))
            add(UiKit.text(this@MainActivity, "Crie um perfil para organizar os arquivos e preparar a instalação.", 13f, UiKit.muted), 6)
        })
        servers.take(limit).forEach { profile ->
            val ready = profiles.jar(profile).isFile
            val running = ServerSession.activeProfileId == profile.id && process.state == ServerState.RUNNING
            body.add(UiKit.card(this).apply {
                add(UiKit.text(this@MainActivity, profile.name, 19f, UiKit.white, true))
                add(UiKit.text(this@MainActivity, "${profile.software}  ·  ${profile.version}", 13f, UiKit.muted), 4)
                add(UiKit.text(this@MainActivity, when {
                    running -> "●  Processo em execução"
                    ready -> "●  JAR importado"
                    else -> "○  Aguardando JAR"
                }, 12f, if (ready || running) UiKit.emerald else UiKit.warning), 10)
                add(UiKit.button(this@MainActivity, "Abrir painel  →") {
                    selected = profile; tab = Tab.CONSOLE; page = Page.DETAIL; render()
                }, 14)
            }, 9)
        }
        if (servers.size > limit) body.add(UiKit.button(this, "Ver todos os servidores") { page = Page.SERVERS; render() }, 9)
    }

    private fun globalFiles() {
        val all = profiles.all()
        body.add(UiKit.text(this, "Escolha um servidor para acessar seus arquivos locais.", 14f, UiKit.muted), bottom = 16)
        if (all.isEmpty()) body.add(UiKit.card(this).apply {
            add(UiKit.text(this@MainActivity, "Nenhum perfil criado", 16f, UiKit.white, true))
        })
        all.forEach { profile ->
            body.add(UiKit.button(this, profile.name, icon = R.drawable.ic_folder) {
                selected = profile; tab = Tab.FILES; page = Page.DETAIL; render()
            }, bottom = 9)
        }
    }

    private fun globalSettings() {
        body.add(UiKit.card(this).apply {
            add(UiKit.text(this@MainActivity, "Diagnóstico da JVM", 18f, UiKit.white, true))
            add(UiKit.text(this@MainActivity, "Veja o estado real do runtime e os logs de inicialização.", 13f, UiKit.muted), 7)
            add(UiKit.button(this@MainActivity, "Abrir diagnóstico", icon = R.drawable.ic_diagnostics) {
                page = Page.DIAGNOSTICS; render()
            }, 14)
        })
        body.add(UiKit.card(this).apply {
            add(UiKit.text(this@MainActivity, "Sobre o aplicativo", 18f, UiKit.white, true))
            add(UiKit.text(this@MainActivity, "Código original, sem anúncios ou pagamentos. O servidor requer um JAR importado e a JVM Android validada.", 13f, UiKit.muted), 7)
        }, 12)
    }

    private fun openWizard(profile: ServerProfile? = null) {
        if (profile == null) selected = null
        editing = profile; step = 0
        name = profile?.name ?: ""
        description = profile?.description ?: ""
        identifier = profile?.identifier ?: ""
        software = profile?.software?.takeIf { it in SOFTWARE } ?: SOFTWARE.first()
        version = profile?.version?.takeUnless { it == "Não informada" } ?: ""
        ram = profile?.ramMb ?: 1024
        difficulty = profile?.difficulty ?: "normal"
        gameMode = profile?.gameMode ?: "survival"
        auth = profile?.officialAuth ?: true
        worldType = profile?.worldType ?: "normal"
        lanEnabled = profile?.lanEnabled ?: true
        tcpPort = profile?.tcpPort ?: 25565
        udpPort = profile?.udpPort ?: 19132
        geyserRequested = profile?.geyserRequested ?: false
        floodgateRequested = profile?.floodgateRequested ?: false
        eula = false
        page = Page.WIZARD; render()
    }

    private fun wizard() {
        val labels = listOf("Informações", "Software", "Versão", "Memória", "Mundo", "Jogadores", "Rede", "Revisão")
        body.add(UiKit.text(this, "ETAPA ${step + 1} DE 8  ·  ${labels[step]}", 12f, UiKit.emerald, true))
        val indicator = UiKit.row(this)
        repeat(8) { index ->
            indicator.addView(View(this).apply { setBackgroundColor(if (index <= step) UiKit.emerald else UiKit.border) },
                LinearLayout.LayoutParams(0, dp(4), 1f).apply { marginEnd = dp(5) })
        }
        body.add(indicator, 11, 20)
        when (step) {
            0 -> wizardIdentity()
            1 -> wizardSoftware()
            2 -> wizardVersion()
            3 -> wizardMemory()
            4 -> wizardWorld()
            5 -> wizardPlayers()
            6 -> wizardNetwork()
            else -> wizardReview()
        }
        val controls = UiKit.row(this)
        if (step > 0) controls.addView(UiKit.button(this, "Anterior") { step--; render() },
            LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(8) })
        controls.addView(UiKit.button(this, if (step == 7) "Salvar perfil" else "Continuar", true) { advance() },
            LinearLayout.LayoutParams(0, -2, 1f))
        body.add(controls, 24)
    }

    private fun wizardIdentity() {
        body.add(UiKit.text(this, "Informações do servidor", 21f, UiKit.white, true))
        body.add(UiKit.text(this, "Dê uma identidade clara ao seu perfil local.", 13f, UiKit.muted), 5, 16)
        body.add(UiKit.field(this, "Nome do servidor", name).apply {
            addTextChangedListener(SimpleTextWatcher { name = it })
        })
        body.add(UiKit.field(this, "Descrição (opcional)", description).apply {
            addTextChangedListener(SimpleTextWatcher { description = it })
        }, 12)
        body.add(UiKit.field(this, "Identificação (opcional)", identifier).apply {
            addTextChangedListener(SimpleTextWatcher { identifier = it })
        }, 12)
        body.add(UiKit.text(this, "A identificação é apenas um rótulo local; ela não reserva domínio nem endereço público.", 12f, UiKit.muted), 11)
    }

    private fun wizardSoftware() {
        body.add(UiKit.text(this, "Escolha o software", 21f, UiKit.white, true))
        body.add(UiKit.text(this, "A seleção organiza o perfil. O JAR será importado por você.", 13f, UiKit.muted), 5, 16)
        SOFTWARE.forEach { option ->
            val chosen = option == software
            body.add(UiKit.card(this, 14).apply {
                add(UiKit.text(this@MainActivity, "${if (chosen) "●" else "○"}  $option", 16f,
                    if (chosen) UiKit.emerald else UiKit.white, true))
                add(UiKit.text(this@MainActivity, when (option) {
                    "Vanilla" -> "Servidor original do Minecraft Java"
                    "Paper", "Purpur" -> "Ecossistema de plugins"
                    "Fabric" -> "Ecossistema de mods Fabric"
                    "Fabric Otimizado" -> "Perfil para JAR Fabric otimizado fornecido por você"
                    else -> "Ecossistema de mods Forge"
                }, 12f, UiKit.muted), 4)
                isClickable = true; setOnClickListener { software = option; render() }
            }, 7)
        }
        if (software == "Fabric Otimizado") body.add(UiKit.text(this,
            "Lithium, FerriteCore, Krypton, C2ME e ServerCore exigem verificação individual de versão e licença. Nenhum mod é instalado neste estágio.",
            12f, UiKit.warning), 12)
    }

    private fun wizardVersion() {
        body.add(UiKit.text(this, "Versão do Minecraft", 21f, UiKit.white, true))
        body.add(UiKit.text(this, "Informe a versão do JAR que você pretende importar. A compatibilidade com $software precisa ser confirmada na origem do JAR.", 13f, UiKit.muted), 5, 16)
        val versionInput = UiKit.field(this, "Ex.: 1.21.1 ou 26.1", version).apply {
            addTextChangedListener(SimpleTextWatcher { version = it })
        }
        body.add(versionInput)
        body.add(UiKit.text(this, "VERSÕES SUGERIDAS", 12f, UiKit.muted, true), 17, 7)
        val suggestions = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        val suggestionRow = UiKit.row(this)
        listOf("1.16.5", "1.18.2", "1.20.1", "1.21.1").forEach { choice ->
            suggestionRow.addView(UiKit.button(this, choice) { versionInput.setText(choice) },
                LinearLayout.LayoutParams(-2, -2).apply { marginEnd = dp(7) })
        }
        suggestions.addView(suggestionRow)
        body.add(suggestions)
        body.add(UiKit.button(this, "Consultar versões oficiais") { loadCatalog() }, 12)
        body.add(UiKit.text(this, catalogStatus, 12f, UiKit.muted), 10)
        catalog?.let { data ->
            body.add(UiKit.button(this, "Usar última estável: ${data.latest}", true) {
                versionInput.setText(data.latest)
            }, 12)
            val recent = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
            val recentRow = UiKit.row(this)
            data.releases.take(12).forEach { release ->
                recentRow.addView(UiKit.button(this, release) { versionInput.setText(release) },
                    LinearLayout.LayoutParams(-2, -2).apply { marginEnd = dp(7) })
            }
            recent.addView(recentRow); body.add(recent, 10)
            body.add(UiKit.text(this, "${data.releases.size} versões estáveis no catálogo. Versões antigas também podem ser digitadas acima.", 12f, UiKit.muted), 10)
            body.add(UiKit.button(this, "Verificar Java desta versão") { checkJavaVersion() }, 10)
        }
        body.add(UiKit.text(this, "O catálogo informa a versão do Minecraft e o Java exigido por ela. A compatibilidade de $software e do JAR importado ainda precisa ser verificada.", 12f, UiKit.warning), 12)
    }

    private fun loadCatalog() {
        catalogStatus = "Consultando o catálogo oficial..."; render()
        Thread({
            val result = runCatching { MinecraftCatalog.fetch() }
            runOnUiThread {
                result.onSuccess { catalog = it; catalogStatus = "Última versão estável informada pela Mojang: ${it.latest}" }
                    .onFailure { catalogStatus = "Falha ao consultar catálogo: ${it.message}" }
                if (page == Page.WIZARD && step == 2) render()
            }
        }, "minecraft-catalog").start()
    }

    private fun checkJavaVersion() {
        val data = catalog ?: return
        val chosen = version.trim()
        catalogStatus = "Verificando requisito de Java..."; render()
        Thread({
            val result = runCatching { MinecraftCatalog.requiredJava(data, chosen) }
            runOnUiThread {
                catalogStatus = result.fold(
                    { "Minecraft $chosen exige Java $it segundo o catálogo oficial. Runtime empacotado: OpenJDK 25, ainda não validado." },
                    { "Não foi possível verificar o Java de $chosen: ${it.message}" }
                )
                if (page == Page.WIZARD && step == 2) render()
            }
        }, "minecraft-java-version").start()
    }

    private fun wizardMemory() {
        val memory = ActivityManager.MemoryInfo()
        (getSystemService(ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(memory)
        val availableMb = memory.availMem / (1024 * 1024)
        val totalMb = memory.totalMem / (1024 * 1024)
        body.add(UiKit.text(this, "Memória do servidor", 21f, UiKit.white, true))
        body.add(UiKit.text(this, "Disponível agora: $availableMb MB  ·  Total: $totalMb MB", 13f, UiKit.muted), 6, 16)
        val value = UiKit.text(this, "$ram MB de limite máximo (Xmx)", 16f, UiKit.emerald, true)
        body.add(value)
        body.add(SeekBar(this).apply {
            max = RAM.lastIndex
            progress = RAM.indexOf(ram).coerceAtLeast(0)
            progressTintList = ColorStateList.valueOf(UiKit.emerald)
            thumbTintList = ColorStateList.valueOf(UiKit.emerald)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar: SeekBar?, position: Int, fromUser: Boolean) {
                    ram = RAM[position]; value.text = "$ram MB de limite máximo (Xmx)"
                }
                override fun onStartTrackingTouch(bar: SeekBar?) = Unit
                override fun onStopTrackingTouch(bar: SeekBar?) = Unit
            })
        }, 12)
        body.add(UiKit.text(this, "Deixe memória livre para o Android e para o próprio servidor. O consumo real depende do JAR e ainda não foi medido.", 12f, UiKit.muted), 12)
    }

    private fun wizardWorld() {
        body.add(UiKit.text(this, "Configurações do mundo", 21f, UiKit.white, true))
        body.add(UiKit.text(this, "As opções serão escritas em server.properties ao iniciar.", 13f, UiKit.muted), 5, 18)
        body.add(UiKit.text(this, "DIFICULDADE", 12f, UiKit.muted, true))
        body.add(spinner(DIFFICULTY_LABELS, DIFFICULTIES.indexOf(difficulty).coerceAtLeast(0)) { difficulty = DIFFICULTIES[it] }, 8, 18)
        body.add(UiKit.text(this, "MODO DE JOGO", 12f, UiKit.muted, true))
        body.add(spinner(MODE_LABELS, MODES.indexOf(gameMode).coerceAtLeast(0)) { gameMode = MODES[it] }, 8, 18)
        body.add(UiKit.text(this, "GERAÇÃO", 12f, UiKit.muted, true))
        body.add(spinner(WORLD_LABELS, WORLD_TYPES.indexOf(worldType).coerceAtLeast(0)) { worldType = WORLD_TYPES[it] }, 8)
    }

    private fun wizardPlayers() {
        body.add(UiKit.text(this, "Jogadores e autenticação", 21f, UiKit.white, true))
        body.add(UiKit.text(this, "O padrão exige a autenticação oficial do Minecraft.", 13f, UiKit.muted), 5, 16)
        body.add(UiKit.card(this).apply {
            add(CheckBox(this@MainActivity).apply {
                text = "Permitir jogadores sem Minecraft original"; setTextColor(UiKit.white)
                buttonTintList = ColorStateList.valueOf(UiKit.emerald); isChecked = !auth
                setOnCheckedChangeListener { _, checked -> auth = !checked }
            })
            add(UiKit.text(this@MainActivity, "Ativar grava online-mode=false. Isso permite falsificação de identidade; use somente em rede confiável com controles adicionais.", 12f, UiKit.warning), 6)
        })
    }

    private fun wizardNetwork() {
        body.add(UiKit.text(this, "Rede e compatibilidade", 21f, UiKit.white, true))
        body.add(UiKit.card(this).apply {
            add(CheckBox(this@MainActivity).apply {
                text = "Acesso pela rede local"; setTextColor(UiKit.white)
                buttonTintList = ColorStateList.valueOf(UiKit.emerald); isChecked = lanEnabled
                setOnCheckedChangeListener { _, checked -> lanEnabled = checked }
            })
            add(UiKit.text(this@MainActivity, "Acesso público depende de túnel ou infraestrutura ainda não implementados.", 12f, UiKit.muted), 5)
        })
        body.add(UiKit.field(this, "Porta TCP", tcpPort.toString()).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            addTextChangedListener(SimpleTextWatcher { tcpPort = it.toIntOrNull() ?: 0 })
        }, 12)
        body.add(UiKit.field(this, "Porta UDP (Bedrock futura)", udpPort.toString()).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            addTextChangedListener(SimpleTextWatcher { udpPort = it.toIntOrNull() ?: 0 })
        }, 12)
        body.add(UiKit.card(this).apply {
            add(UiKit.text(this@MainActivity, "Integrações Bedrock", 16f, UiKit.white, true))
            add(UiKit.text(this@MainActivity, "GeyserMC e Floodgate: indisponíveis até verificar o JAR, a versão e as dependências. Nenhum plugin é instalado nesta etapa.", 12f, UiKit.muted), 6)
            listOf("GeyserMC · indisponível", "Floodgate · indisponível").forEach { label ->
                add(CheckBox(this@MainActivity).apply {
                    text = label; setTextColor(UiKit.muted); isEnabled = false
                }, 7)
            }
        }, 10)
    }

    private fun wizardReview() {
        body.add(UiKit.text(this, "Revisar e criar", 21f, UiKit.white, true))
        body.add(UiKit.card(this).apply {
            add(UiKit.text(this@MainActivity, name.ifBlank { "Sem nome" }, 18f, UiKit.white, true))
            add(UiKit.text(this@MainActivity, "$software · $version · $ram MB", 13f, UiKit.muted), 6)
            add(UiKit.text(this@MainActivity, "${difficultyLabel(difficulty)} · ${modeLabel(gameMode)} · Autenticação ${if (auth) "ativada" else "desativada"}", 13f, UiKit.muted), 4)
            add(UiKit.text(this@MainActivity, "Mundo: ${worldLabel(worldType)} · LAN: ${if (lanEnabled) "sim" else "não"} · TCP: $tcpPort", 13f, UiKit.muted), 4)
            add(UiKit.text(this@MainActivity, "GeyserMC/Floodgate: indisponíveis · Acesso público: indisponível", 12f, UiKit.warning), 8)
        }, 8, 18)
        body.add(UiKit.button(this, "Ler a EULA oficial ↗") {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.minecraft.net/eula")))
        })
        body.add(CheckBox(this).apply {
            text = "Li e aceito a EULA do Minecraft"; setTextColor(UiKit.white)
            buttonTintList = ColorStateList.valueOf(UiKit.emerald); isChecked = eula
            setOnCheckedChangeListener { _, checked -> eula = checked }
        }, 12)
        body.add(UiKit.text(this, "Nenhum JAR é baixado nesta etapa. Importe um arquivo obtido por você no painel.", 12f, UiKit.muted), 8)
    }

    private fun advance() {
        if (step == 0 && name.trim().isEmpty()) { toast("Informe um nome para o servidor."); return }
        if (step == 2 && version.trim().isEmpty()) { toast("Informe a versão do Minecraft."); return }
        if (step == 6 && (tcpPort !in 1..65535 || udpPort !in 1..65535)) {
            toast("Use portas entre 1 e 65535."); return
        }
        if (step < 7) { step++; render(); return }
        if (!eula) { toast("Leia e aceite a EULA para salvar o perfil."); return }
        val profile = (editing ?: ServerProfile(ServerProfiles.newId(), "", "", "", 1024, "normal", "survival", true, false)).apply {
            this.name = this@MainActivity.name.trim(); this.software = this@MainActivity.software
            this.version = this@MainActivity.version.trim(); ramMb = ram
            this.difficulty = this@MainActivity.difficulty; gameMode = this@MainActivity.gameMode
            officialAuth = auth; eulaAccepted = eula
            description = this@MainActivity.description.trim()
            identifier = this@MainActivity.identifier.trim()
            worldType = this@MainActivity.worldType
            lanEnabled = this@MainActivity.lanEnabled
            tcpPort = this@MainActivity.tcpPort; udpPort = this@MainActivity.udpPort
            geyserRequested = this@MainActivity.geyserRequested
            floodgateRequested = this@MainActivity.floodgateRequested
        }
        runCatching {
            if (editing == null) profiles.create(profile) else profiles.update(profile)
            selected = profile; editing = null; tab = Tab.CONSOLE; page = Page.DETAIL; render()
        }.onFailure { toast("Não foi possível salvar: ${it.message}") }
    }

    private fun detail() {
        val profile = selected ?: return
        val jar = profiles.jar(profile)
        body.add(UiKit.card(this).apply {
            add(UiKit.text(this@MainActivity, "${profile.software}  ·  ${profile.version}", 14f, UiKit.muted))
            statusView = UiKit.text(this@MainActivity, "", 17f, UiKit.emerald, true).also { add(it, 8) }
            add(UiKit.text(this@MainActivity, if (jar.isFile) "JAR importado · ${jar.length() / 1048576} MB" else "JAR ainda não importado", 12f, UiKit.muted), 6)
            add(UiKit.button(this@MainActivity, if (jar.isFile) "Trocar JAR do servidor" else "Importar JAR do servidor") {
                pickFile(profiles.directory(profile), true)
            }, 14)
        })
        val tabs = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        val tabRow = UiKit.row(this)
        Tab.entries.forEach { option ->
            val icon = when (option) {
                Tab.CONSOLE -> R.drawable.ic_terminal
                Tab.SETTINGS -> R.drawable.ic_settings
                Tab.DIAGNOSTICS -> R.drawable.ic_diagnostics
                else -> R.drawable.ic_folder
            }
            val item = UiKit.button(this, option.label, icon = icon) { tab = option; viewedFolder = null; render() }
            item.setTextColor(if (tab == option) UiKit.emerald else UiKit.muted)
            tabRow.addView(item, LinearLayout.LayoutParams(-2, -2).apply { marginEnd = dp(7) })
        }
        tabs.addView(tabRow); body.add(tabs, 16, 18)
        when (tab) {
            Tab.CONSOLE -> console(profile)
            Tab.FILES -> files(profile, "")
            Tab.WORLDS -> files(profile, "world")
            Tab.MODS -> files(profile, "mods")
            Tab.PLUGINS -> files(profile, "plugins")
            Tab.BACKUPS -> planned("Backups", "A criação e restauração de cópias ainda não foram implementadas.")
            Tab.SETTINGS -> settings(profile)
            Tab.DIAGNOSTICS -> diagnostics()
        }
    }

    private fun planned(title: String, explanation: String) {
        body.add(UiKit.card(this).apply {
            add(UiKit.text(this@MainActivity, title, 18f, UiKit.white, true))
            add(UiKit.text(this@MainActivity, "Indisponível · $explanation", 13f, UiKit.muted), 7)
        })
    }

    private fun console(profile: ServerProfile) {
        body.add(UiKit.text(this, "CONSOLE", 12f, UiKit.muted, true))
        val controls = UiKit.row(this)
        startView = UiKit.button(this, "▶  Iniciar", true) { startServer(profile) }.also {
            controls.addView(it, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(8) })
        }
        stopView = UiKit.button(this, "■  Parar") {
            runCatching { process.requestStop() }.onFailure { ServerSession.note("Falha ao parar: ${it.message}") }
        }.also { controls.addView(it, LinearLayout.LayoutParams(0, -2, 1f)) }
        body.add(controls, 10, 14)
        terminal(210)
        val command = UiKit.row(this)
        commandView = UiKit.field(this, "Comando do servidor").also {
            command.addView(it, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(7) })
        }
        sendView = UiKit.button(this, "Enviar", true) {
            val value = commandView?.text?.toString()?.trim().orEmpty()
            if (value.isNotEmpty()) runCatching { process.send(value); commandView?.text?.clear() }
                .onFailure { ServerSession.note("Falha ao enviar: ${it.message}") }
        }.also { command.addView(it) }
        body.add(command, 12)
        body.add(UiKit.text(this, "O console só funcionará após a JVM iniciar no Android.", 12f, UiKit.muted), 12)
    }

    private fun terminal(height: Int) {
        val panel = UiKit.card(this)
        logView = UiKit.text(this, "", 12f, 0xFFC5E7CF.toInt()).apply {
            typeface = android.graphics.Typeface.MONOSPACE
            setTextIsSelectable(true); minHeight = dp(height)
        }
        panel.add(logView!!); body.add(panel)
    }

    private fun files(profile: ServerProfile, subfolder: String) {
        val base = profiles.directory(profile)
        val folder = if (subfolder.isEmpty()) base else File(base, subfolder)
        if (!folder.isDirectory) folder.mkdirs()
        if (!folder.isDirectory || (folder.canonicalPath != base.canonicalPath &&
                    !folder.canonicalPath.startsWith(base.canonicalPath + File.separator))) {
            body.add(UiKit.text(this, "Pasta indisponível ou fora da área do servidor.", 13f, UiKit.warning))
            return
        }
        val current = viewedFolder?.takeIf {
            it.canonicalPath == folder.canonicalPath || it.canonicalPath.startsWith(folder.canonicalPath + File.separator)
        } ?: folder
        viewedFolder = current
        body.add(UiKit.text(this, when (subfolder) { "mods" -> "MODS"; "plugins" -> "PLUGINS"; "world" -> "MUNDOS"; else -> "ARQUIVOS" }, 12f, UiKit.muted, true))
        body.add(UiKit.text(this, if (current == folder) "Pasta do servidor" else current.relativeTo(folder).path, 15f, UiKit.white, true), 7)
        if (subfolder.isNotEmpty()) body.add(UiKit.text(this, "A pasta pode ainda não conter dados. Mods e plugins não são instalados automaticamente.", 12f, UiKit.muted), 8)
        if (current != folder) body.add(UiKit.button(this, "←  Pasta anterior") { viewedFolder = current.parentFile; render() }, 12)
        body.add(UiKit.button(this, "+  Importar arquivo", true) { pickFile(current) }, 12, 12)
        val entries = current.listFiles()?.sortedWith(compareBy<File>({ !it.isDirectory }, { it.name.lowercase() })).orEmpty()
        if (entries.isEmpty()) body.add(UiKit.card(this).apply {
            add(UiKit.text(this@MainActivity, "Esta pasta está vazia.", 13f, UiKit.muted))
        })
        entries.forEach { entry ->
            body.add(UiKit.card(this, 14).apply {
                add(UiKit.text(this@MainActivity, "${if (entry.isDirectory) "▣" else "▤"}  ${entry.name}", 14f, UiKit.white, true))
                if (!entry.isDirectory) add(UiKit.text(this@MainActivity, "${entry.length() / 1024} KB", 11f, UiKit.muted), 3)
                isClickable = true
                setOnClickListener {
                    if (entry.isDirectory) { viewedFolder = entry; render() }
                    else AlertDialog.Builder(this@MainActivity).setTitle(entry.name)
                        .setMessage("Arquivo do servidor · ${entry.length()} bytes")
                        .setPositiveButton("OK", null).show()
                }
            }, 6)
        }
    }

    private fun settings(profile: ServerProfile) {
        body.add(UiKit.card(this).apply {
            add(UiKit.text(this@MainActivity, "Configuração do servidor", 17f, UiKit.white, true))
            add(UiKit.text(this@MainActivity, "${profile.ramMb} MB · ${difficultyLabel(profile.difficulty)} · ${modeLabel(profile.gameMode)}", 13f, UiKit.muted), 8)
            add(UiKit.text(this@MainActivity, "TCP ${profile.tcpPort} · LAN ${if (profile.lanEnabled) "ativada" else "desativada"}", 13f, UiKit.muted), 5)
            add(UiKit.text(this@MainActivity, "Tipo de mundo ${worldLabel(profile.worldType)}: salvo no perfil; aplicação depende de validar a versão do JAR.", 12f, UiKit.warning), 5)
            add(UiKit.text(this@MainActivity, "Autenticação oficial: ${if (profile.officialAuth) "ativada" else "desativada"}", 13f, UiKit.muted), 5)
            add(UiKit.text(this@MainActivity, "EULA: ${if (profile.eulaAccepted) "aceita" else "pendente"}", 13f, UiKit.muted), 5)
            add(UiKit.button(this@MainActivity, "Editar escolhas") { openWizard(profile) }, 16)
        })
        body.add(UiKit.card(this).apply {
            add(UiKit.text(this@MainActivity, "GeyserMC e Floodgate", 16f, UiKit.white, true))
            add(UiKit.text(this@MainActivity, "Integração ainda não implementada. Gerencie arquivos manualmente nas abas Mods e Plugins.", 12f, UiKit.muted), 6)
        }, 12)
    }

    private fun diagnostics() {
        body.add(UiKit.card(this).apply {
            add(UiKit.text(this@MainActivity, "OpenJDK 25 · ARM64", 18f, UiKit.white, true))
            add(UiKit.text(this@MainActivity, "Teste real da JVM. Logs e erros aparecem integralmente abaixo.", 13f, UiKit.muted), 7)
            statusView = UiKit.text(this@MainActivity, "", 13f, UiKit.warning).also { add(it, 12) }
            testView = UiKit.button(this@MainActivity, "▶  Testar JVM Android", true) { testJvm() }.also { add(it, 14) }
        })
        body.add(UiKit.text(this, "O Galaxy S23 FE confirmou: a JVM procura lib/modules a partir da pasta nativa do APK. Este build do OpenJDK ainda precisa de adaptação; o teste mostra o erro real no console.", 13f, UiKit.muted), 16, 14)
        terminal(230)
    }

    private fun startServer(profile: ServerProfile) {
        if (!ServerSession.beginPrepare()) return
        Thread({
            try {
                val jar = profiles.jar(profile)
                check(jar.isFile) { "Importe um JAR antes de iniciar." }
                ServerConfiguration.prepare(profiles.directory(profile), profile)
                ServerSession.activeProfileId = profile.id
                process.start(runtime.launch(jar, profile.ramMb))
            } catch (error: Exception) { ServerSession.note("Falha ao iniciar: ${error.message}") }
            finally { ServerSession.endPrepare() }
        }, "server-starter").start()
    }

    private fun testJvm() {
        if (!ServerSession.beginPrepare()) return
        Thread({
            try {
                ServerSession.note("Iniciando teste real da JVM Android...")
                ServerSession.activeProfileId = null
                process.start(runtime.selfTest())
            } catch (error: Exception) { ServerSession.note("Falha no teste: ${error.message}") }
            finally { ServerSession.endPrepare() }
        }, "jvm-self-test").start()
    }

    private fun updateLive() {
        if (!::root.isInitialized) return
        val state = process.state
        val reason = runtime.unavailableReason()
        val busy = ServerSession.preparing || state !in listOf(ServerState.STOPPED, ServerState.FAILED)
        statusView?.text = when {
            ServerSession.preparing -> "Preparando runtime..."
            reason != null -> "Runtime indisponível: $reason"
            state == ServerState.RUNNING -> "Processo em execução · JVM não validada"
            state == ServerState.STARTING -> "Iniciando processo..."
            state == ServerState.STOPPING -> "Encerrando processo..."
            else -> "Processo parado"
        }
        logView?.text = ServerSession.consoleText().ifBlank { "Nenhum registro ainda." }
        startView?.isEnabled = !busy && reason == null && selected?.let { profiles.jar(it).isFile && it.eulaAccepted } == true
        stopView?.isEnabled = state == ServerState.RUNNING && selected?.id == ServerSession.activeProfileId
        testView?.isEnabled = !busy && reason == null
        commandView?.isEnabled = state == ServerState.RUNNING && selected?.id == ServerSession.activeProfileId
        sendView?.isEnabled = commandView?.isEnabled == true
        listOf(startView, stopView, testView, sendView).forEach { it?.alpha = if (it?.isEnabled == true) 1f else 0.45f }
    }

    private fun pickFile(folder: File, serverJar: Boolean = false) {
        val profile = selected ?: return
        if (ServerSession.activeProfileId == profile.id && process.state !in listOf(ServerState.STOPPED, ServerState.FAILED)) {
            toast("Pare o servidor antes de alterar seus arquivos.")
            return
        }
        val root = profiles.directory(profile).canonicalPath
        val current = folder.canonicalPath
        if (current != root && !current.startsWith(root + File.separator)) {
            toast("A pasta escolhida está fora da área do servidor.")
            return
        }
        importFolder = folder; importingJar = serverJar
        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }, REQUEST_IMPORT)
    }

    @Deprecated("Uses the platform document picker without another dependency")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_IMPORT || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        val folder = importFolder ?: return
        val name = if (importingJar) "server.jar" else runCatching {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) it.getString(0) else null
            }
        }.getOrNull() ?: "arquivo-importado"
        if (name.isBlank() || name == "." || name == ".." || '/' in name || '\\' in name) {
            toast("Nome de arquivo inválido."); return
        }
        val target = File(folder, name)
        if (target.exists()) AlertDialog.Builder(this).setTitle("Substituir arquivo?")
            .setMessage("O arquivo $name já existe.").setNegativeButton("Cancelar", null)
            .setPositiveButton("Substituir") { _, _ -> copyDocument(uri, target) }.show()
        else copyDocument(uri, target)
    }

    private fun copyDocument(uri: Uri, target: File) {
        val validateJar = importingJar
        Thread({
            var temporary: File? = null
            try {
                check(target.parentFile?.isDirectory == true)
                val profile = selected ?: error("Servidor não selecionado")
                val rootPath = profiles.directory(profile).canonicalPath
                val folderPath = target.parentFile!!.canonicalPath
                check(folderPath == rootPath || folderPath.startsWith(rootPath + File.separator))
                val staging = Files.createTempFile(target.parentFile!!.toPath(), ".openmc-", ".part").toFile()
                temporary = staging
                val source = contentResolver.openInputStream(uri) ?: error("Não foi possível abrir o arquivo")
                source.use { input -> staging.outputStream().use { output -> input.copyTo(output) } }
                if (validateJar) {
                    val header = ByteArray(4)
                    check(staging.inputStream().use { it.read(header) } == 4 &&
                        header.contentEquals(byteArrayOf(0x50, 0x4b, 0x03, 0x04))) { "O arquivo escolhido não é um JAR ZIP válido." }
                }
                Files.move(staging.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
                runOnUiThread { toast("Arquivo importado: ${target.name}"); render() }
            } catch (error: Exception) {
                temporary?.delete()
                runOnUiThread { toast("Falha na importação: ${error.message}") }
            }
        }, "document-import").start()
    }

    private fun spinner(items: List<String>, initial: Int, changed: (Int) -> Unit) = Spinner(this).apply {
        adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, items)
        setSelection(initial)
        onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) = changed(position)
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
        setPadding(dp(8), dp(8), dp(8), dp(8))
        setBackgroundColor(UiKit.elevated)
    }

    private fun goBack() {
        when (page) {
            Page.WIZARD -> { editing = null; page = if (selected == null) Page.SERVERS else Page.DETAIL }
            Page.DETAIL, Page.DIAGNOSTICS -> page = Page.SERVERS
            Page.SERVERS, Page.FILES, Page.SETTINGS -> page = Page.HOME
            Page.HOME -> Unit
        }
        render()
    }
    @Deprecated("Uses the Activity back callback")
    override fun onBackPressed() { if (page == Page.HOME) super.onBackPressed() else goBack() }
    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    private fun difficultyLabel(value: String): String = DIFFICULTY_LABELS.getOrElse(DIFFICULTIES.indexOf(value)) { value }
    private fun modeLabel(value: String): String = MODE_LABELS.getOrElse(MODES.indexOf(value)) { value }
    private fun worldLabel(value: String): String = WORLD_LABELS.getOrElse(WORLD_TYPES.indexOf(value)) { value }

    private enum class Page { HOME, SERVERS, WIZARD, FILES, SETTINGS, DETAIL, DIAGNOSTICS }
    private enum class Tab(val label: String) {
        CONSOLE("Console"), FILES("Arquivos"), WORLDS("Mundos"), MODS("Mods"),
        PLUGINS("Plugins"), BACKUPS("Backups"), SETTINGS("Ajustes"), DIAGNOSTICS("Diagnóstico")
    }
    companion object {
        private const val REQUEST_IMPORT = 1001
        private val SOFTWARE = listOf("Vanilla", "Paper", "Purpur", "Fabric", "Fabric Otimizado", "Forge", "NeoForge")
        private val RAM = listOf(512, 1024, 1536, 2048, 3072, 4096, 6144)
        private val DIFFICULTIES = listOf("peaceful", "easy", "normal", "hard")
        private val DIFFICULTY_LABELS = listOf("Pacífico", "Fácil", "Normal", "Difícil")
        private val MODES = listOf("survival", "creative", "adventure", "spectator")
        private val MODE_LABELS = listOf("Sobrevivência", "Criativo", "Aventura", "Espectador")
        private val WORLD_TYPES = listOf("normal", "flat", "large_biomes", "amplified")
        private val WORLD_LABELS = listOf("Normal", "Plano", "Biomas grandes", "Amplificado")
    }
}
