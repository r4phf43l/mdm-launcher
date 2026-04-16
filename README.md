# CityHub MDM Agent & Launcher

> Portal de aplicativos institucional e Launcher gerenciável via MDM.

---

## 1. Visão geral

O **CityHub Agent** é uma solução dual que funciona tanto como um portal de aplicativos quanto como o **Launcher principal** do dispositivo. Ele permite o controle total sobre quais aplicativos o usuário pode acessar e personaliza a identidade visual do sistema (Home e Lock Screen) remotamente via MDM.

- **Launcher Nativo:** Pode ser definido como a tela inicial padrão (Categoria `HOME`).
- **Início Inteligente:** Inicia automaticamente no boot ou após atualizações. Se já configurado, roda silenciosamente em segundo plano; caso contrário, guia o usuário pelo Onboarding.
- **Kiosk Mode:** Bloqueia o acesso a configurações e aplicativos não autorizados através de um serviço de monitoramento persistente.
- **Home Customizada:** Exibe uma grade de aplicativos selecionados na tela inicial para acesso rápido.
- **Identidade Visual Dinâmica:** Força a aplicação de wallpapers de Home e Lock Screen via MDM.
- **Gestão Remota:** Configurações via **Managed Configuration** (Android Enterprise).

**Compatibilidade:** Android 4.4 KitKat (API 19) até Android 14 (API 34).
**Versão Atual:** 1.4.0 (Build 9)

---

## 2. Funcionalidades Principais

### 2.1 Controle de Aplicativos
- **Home Apps:** Seleção de quais aplicativos aparecerão na grade da tela inicial.
- **Filtros Flexíveis:** Suporte a `allowlist` (apenas autorizados) ou `denylist` (bloqueio seletivo).
- **Bloqueio de Configurações:** Impede que o usuário final altere ajustes críticos do sistema.
- **Monitoramento em Segundo Plano:** Serviço persistente que garante a conformidade do dispositivo em tempo real.

### 2.2 Personalização e Wallpaper
- **Force Wallpaper:** Quando configurado via MDM, o agente garante a aplicação imediata da imagem, ignorando restrições de economia de bateria.
- **Suporte a Cores:** Fundo do portal customizável via Hexadecimal (#RRGGBB).

---

## 3. Configuração via MDM (Managed Configuration)

| Chave MDM | Tipo | Descrição |
|---|---|---|
| `bg_color` | string | Cor hexadecimal (#RRGGBB) para o portal. |
| `welcome_text` | string | Texto de boas-vindas na Home. |
| `home_apps` | string | Pacotes que aparecerão na Home (vírgula). |
| `admin_password` | string | Senha para acessar as configurações no dispositivo. |
| `app_filter_mode` | choice | `none` / `allowlist` / `denylist`. |
| `allowed_apps` | string | Pacotes autorizados no sistema. |
| `denied_apps` | string | Pacotes bloqueados no sistema. |
| `lock_wallpaper_enabled` | bool | Ativa/Desativa wallpaper customizado na Lock Screen. |
| `lock_wallpaper_uri` | string | URI/URL da imagem para a tela de bloqueio. |
| `block_settings` | bool | Bloqueia o app de Configurações do Android. |
| `auto_start` | bool | Inicia o agente no boot e atualizações. |

---

## 4. Comportamento de Inicialização

1. **Boot / Instalação / Update:**
   - O sistema dispara o `BootReceiver`.
   - **Configurado?** Inicia o `OverlayService` em segundo plano e aplica o Wallpaper/Config do MDM silenciosamente.
   - **Não configurado?** Abre a tela de `Onboarding` para setup manual.
2. **Clique no Ícone:**
   - Abre sempre a `MainActivity` (Grade de apps da Home).

---

## Desenvolvedor

- **RafaAntonio (R4PHF43L)**
- **GitHub:** [github.com/r4phf43l](https://github.com/r4phf43l)
- **E-mail:** rafael.aantonio@gmail.com

---

## Licença

Este projeto está licenciado sob a Licença MIT - veja o arquivo [LICENSE](LICENSE) para detalhes.
