# Pixel Shelf (Catálogo de Jogos)

Trabalho da disciplina de **Desenvolvimento Mobile II**.

## Integrantes
- Renan Vinciguerra Mentz — 198321
- Samuel Vitor Zibetti — 200388

## Descrição da proposta
Aplicativo Android para catalogar jogos com descrição completa: título, plataforma,
status (**Jogando**, **Zerado**, **Na fila**), nota (0 a 10) e horas jogadas.

### Estado atual (primeira versão)
- [x] Tela de login (simulada: usuário `admin`, senha `1234`)
- [x] Tela principal com a estante de jogos (RecyclerView)
- [x] Cadastro de jogo com validações (Create)
- [x] Listagem (Read) e detalhe com exclusão (Delete)
- [ ] Edição de jogo (Update)
- [ ] Filtro por status (Fragments)

## Tecnologias utilizadas
- Kotlin
- Android Studio (Views XML + ViewBinding)
- Room (SQLite) para persistência
- Coroutines / Flow
- Material Components
- Git e GitHub

## Instruções de execução
1. Clonar: `git clone https://github.com/RenanMentz/pixel_shelf.git`
2. Abrir a pasta no Android Studio e aguardar o *Gradle Sync*.
3. Executar (`Run ▶`) em um emulador ou dispositivo com Android 7.0 (API 24) ou superior.
4. Entrar com usuário `admin` e senha `1234`.

## Prints das telas
Ver a pasta [`screenshots/`](screenshots/).
