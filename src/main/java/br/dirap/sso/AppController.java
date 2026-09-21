package br.dirap.sso;

import java.security.Principal;
import java.sql.PreparedStatement;
import java.time.LocalDate;
import java.time.YearMonth;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Controller
public class AppController {
    private final JdbcTemplate db;
    private final PasswordEncoder encoder;
    public AppController(JdbcTemplate db, PasswordEncoder encoder) { this.db=db; this.encoder=encoder; }

    private Long userId(Principal p) {
        return db.queryForObject("SELECT id FROM usuarios WHERE lower(email)=lower(?)", Long.class, p.getName());
    }
    private void common(Model m, Principal p) {
        m.addAttribute("usuario", db.queryForObject("SELECT nome FROM usuarios WHERE lower(email)=lower(?)", String.class, p.getName()));
        m.addAttribute("administrador", p instanceof Authentication auth && auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority())));
    }
    private List<Map<String,Object>> rows(String sql, Object... args) { return db.queryForList(sql, args); }

    @GetMapping("/login") String login() { return "login"; }
    @GetMapping("/") String home() { return "redirect:/dashboard"; }

    @GetMapping("/dashboard") String dashboard(Model m, Principal p) {
        common(m,p);
        long total = db.queryForObject("SELECT count(*) FROM atendimentos", Long.class);
        long pendentes = db.queryForObject("SELECT count(*) FROM atendimentos WHERE status='Pendente'", Long.class);
        long resolvidos = db.queryForObject("SELECT count(*) FROM atendimentos WHERE status='Resolvido'", Long.class);
        m.addAttribute("total", total);
        m.addAttribute("pendentes", pendentes);
        m.addAttribute("resolvidos", resolvidos);
        m.addAttribute("pendentesPercentual", total == 0 ? 0 : Math.round(100.0 * pendentes / total));
        m.addAttribute("resolvidosPercentual", total == 0 ? 0 : Math.round(100.0 * resolvidos / total));
        m.addAttribute("evolucoes", db.queryForObject("SELECT count(*) FROM evolucoes", Long.class));
        m.addAttribute("registros", rows("SELECT a.id,a.protocolo,a.nome,a.paciente,a.saram,a.status,to_char(a.criado_em,'DD/MM/YYYY') AS data,u.nome AS cadastrador FROM atendimentos a JOIN usuarios u ON u.id=a.criado_por ORDER BY a.id DESC LIMIT 10"));
        m.addAttribute("evolucoesRecentes", rows("SELECT e.id, a.id AS atendimento_id, a.protocolo, a.nome, a.paciente, a.saram, " +
                "e.status_apos AS status, u.nome AS cadastrador, to_char(e.criado_em,'DD/MM/YYYY') AS data " +
                "FROM evolucoes e JOIN atendimentos a ON a.id=e.atendimento_id JOIN usuarios u ON u.id=e.autor_id " +
                "ORDER BY e.criado_em DESC, e.id DESC LIMIT 10"));
        m.addAttribute("atendimentosMensais", rows("SELECT to_char(m.mes,'MM/YYYY') AS mes, count(a.id) AS total, " +
                "round(100.0 * count(a.id) / greatest(max(count(a.id)) OVER (),1)) AS percentual " +
                "FROM generate_series(date_trunc('month',CURRENT_DATE)-interval '5 months', " +
                "date_trunc('month',CURRENT_DATE), interval '1 month') AS m(mes) " +
                "LEFT JOIN atendimentos a ON a.criado_em >= m.mes AND a.criado_em < m.mes + interval '1 month' " +
                "GROUP BY m.mes ORDER BY m.mes"));
        return "dashboard";
    }

    @GetMapping("/atendimentos/novo") String novo(Model m, Principal p) { common(m,p); m.addAttribute("form",new AtendimentoForm()); return "novo"; }

    @PostMapping("/atendimentos") @Transactional String criar(@Valid @ModelAttribute("form") AtendimentoForm f, BindingResult errors,
            Model m, Principal p, RedirectAttributes flash) {
        if (errors.hasErrors()) { common(m,p); m.addAttribute("formError",true); return "novo"; }
        Long autor=userId(p);
        GeneratedKeyHolder key=new GeneratedKeyHolder();
        db.update(con -> {
            PreparedStatement ps=con.prepareStatement("INSERT INTO atendimentos(nome,paciente,saram,idade,bairro,residencia,celular,vinculo,meio_contato,categoria,situacao,tipo,classificacao,status,estado,descricao,encaminhamento,criado_por) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)", new String[]{"id"});
            Object[] v={f.nome,f.paciente,f.saram,f.idade,f.bairro,f.residencia,f.celular,f.vinculo,f.meioContato,f.categoria,f.situacao,f.tipo,f.classificacao,f.status,f.estado,f.descricao,f.encaminhamento,autor};
            for(int i=0;i<v.length;i++) ps.setObject(i+1,v[i]); return ps;
        },key);
        long id=key.getKey().longValue();
        String protocolo=String.format("SSO-%d-%06d", LocalDate.now().getYear(),id);
        db.update("UPDATE atendimentos SET protocolo=? WHERE id=?",protocolo,id);
        flash.addFlashAttribute("mensagem","Atendimento salvo. Protocolo: " + protocolo);
        return "redirect:/atendimentos/"+id;
    }

    @GetMapping("/atendimentos") String listar(@RequestParam(defaultValue="") String busca, Model m, Principal p) {
        common(m,p); m.addAttribute("busca",busca);
        String q="%"+busca.trim()+"%";
        m.addAttribute("registros", rows("SELECT a.id,a.protocolo,a.nome,a.paciente,a.saram,a.status,to_char(a.criado_em,'DD/MM/YYYY') AS data,u.nome AS cadastrador FROM atendimentos a JOIN usuarios u ON u.id=a.criado_por WHERE lower(a.nome) LIKE lower(?) OR lower(a.paciente) LIKE lower(?) OR a.saram LIKE ? OR lower(a.protocolo) LIKE lower(?) ORDER BY a.id DESC LIMIT 200",q,q,q,q));
        return "atendimentos";
    }

    @GetMapping("/evolucoes") String listarEvolucoes(Model m, Principal p) {
        common(m,p);
        m.addAttribute("registros", rows("SELECT e.id, a.id AS atendimento_id, a.protocolo, a.nome, a.paciente, a.saram, " +
                "e.status_apos AS status, u.nome AS cadastrador, to_char(e.criado_em,'DD/MM/YYYY') AS data " +
                "FROM evolucoes e JOIN atendimentos a ON a.id=e.atendimento_id " +
                "JOIN usuarios u ON u.id=e.autor_id ORDER BY e.criado_em DESC, e.id DESC LIMIT 50"));
        return "evolucoes";
    }

    @GetMapping("/atendimentos/{id}") String detalhe(@PathVariable long id, Model m, Principal p) {
        common(m,p);
        List<Map<String,Object>> found=rows("SELECT a.*,u.nome AS cadastrador FROM atendimentos a JOIN usuarios u ON u.id=a.criado_por WHERE a.id=?",id);
        if(found.isEmpty()) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND);
        m.addAttribute("a",found.get(0));
        m.addAttribute("historico",rows("SELECT e.*,to_char(e.criado_em,'DD/MM/YYYY HH24:MI') AS data,u.nome AS autor FROM evolucoes e JOIN usuarios u ON u.id=e.autor_id WHERE e.atendimento_id=? ORDER BY e.id DESC",id));
        m.addAttribute("evolucaoForm",new EvolucaoForm()); return "detalhe";
    }

    @PostMapping("/atendimentos/{id}/evolucoes") @Transactional String evoluir(@PathVariable long id,
            @Valid @ModelAttribute EvolucaoForm f, BindingResult errors, Principal p, RedirectAttributes flash) {
        if(errors.hasErrors()) { flash.addFlashAttribute("erro","Preencha a evolução (até 1500 caracteres) e escolha um status válido."); return "redirect:/atendimentos/"+id; }
        int updated=db.update("UPDATE atendimentos SET status=?, atualizado_em=CURRENT_TIMESTAMP, versao=versao+1 WHERE id=? AND versao=?",f.status,id,f.versao);
        if(updated==0) { flash.addFlashAttribute("erro","O atendimento foi alterado por outra pessoa. Recarregue e tente novamente."); return "redirect:/atendimentos/"+id; }
        db.update("INSERT INTO evolucoes(atendimento_id,texto,status_apos,autor_id) VALUES (?,?,?,?)",id,f.texto.trim(),f.status,userId(p));
        flash.addFlashAttribute("mensagem","Evolução salva."); return "redirect:/atendimentos/"+id;
    }

    @GetMapping("/relatorios") String relatorios(@RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate fim, Model m, Principal p) {
        common(m,p); m.addAttribute("inicio",inicio);m.addAttribute("fim",fim);
        m.addAttribute("mesAtual", YearMonth.now().toString());
        if(inicio!=null && fim!=null && !fim.isBefore(inicio)) {
            m.addAttribute("resumo",rows("SELECT count(*) AS total, count(*) FILTER (WHERE status='Resolvido') AS resolvidos, count(*) FILTER (WHERE status='Pendente') AS pendentes FROM atendimentos WHERE criado_em >= ? AND criado_em < ?",inicio.atStartOfDay(),fim.plusDays(1).atStartOfDay()).get(0));
            m.addAttribute("porCategoria",rows("SELECT coalesce(nullif(categoria,''),'Não informada') AS categoria,count(*) AS total FROM atendimentos WHERE criado_em >= ? AND criado_em < ? GROUP BY 1 ORDER BY total DESC",inicio.atStartOfDay(),fim.plusDays(1).atStartOfDay()));
        }
        return "relatorios";
    }

    @GetMapping("/relatorios/atendimentos-completos.xlsx") void atendimentosCompletos(HttpServletResponse response) throws IOException {
        var registros = rows("SELECT a.id AS \"ID Cadastro\", a.protocolo AS \"Protocolo\", " +
                "a.nome AS \"Nome\", a.paciente AS \"Paciente\", a.saram AS \"SARAM\", " +
                "a.idade AS \"Idade\", a.bairro AS \"Bairro\", a.residencia AS \"Residência\", " +
                "a.celular AS \"Celular\", a.vinculo AS \"Vínculo\", a.meio_contato AS \"Meio de contato\", " +
                "a.categoria AS \"Categoria\", a.situacao AS \"Situação\", a.tipo AS \"Tipo\", " +
                "a.classificacao AS \"Classificação\", a.status AS \"Status\", a.estado AS \"Estado\", " +
                "a.descricao AS \"Descrição inicial\", a.encaminhamento AS \"Encaminhamento\", " +
                "u.nome AS \"Cadastrador\", to_char(a.criado_em,'DD/MM/YYYY HH24:MI') AS \"Criado em\", " +
                "to_char(a.atualizado_em,'DD/MM/YYYY HH24:MI') AS \"Atualizado em\" " +
                "FROM atendimentos a JOIN usuarios u ON u.id=a.criado_por ORDER BY a.id");
        XlsxExport.write(response, "atendimentos-completos.xlsx", "Atendimentos", registros,
                List.of("ID Cadastro","Protocolo","Nome","Paciente","SARAM","Idade","Bairro","Residência",
                        "Celular","Vínculo","Meio de contato","Categoria","Situação","Tipo","Classificação",
                        "Status","Estado","Descrição inicial","Encaminhamento","Cadastrador","Criado em","Atualizado em"));
    }

    @GetMapping("/relatorios/atendimentos-por-mes.xlsx") void atendimentosPorMes(
            @RequestParam @org.springframework.format.annotation.DateTimeFormat(pattern="yyyy-MM") YearMonth mes,
            HttpServletResponse response) throws IOException {
        var registros = rows("SELECT a.id AS \"ID Cadastro\", a.protocolo AS \"Protocolo\", " +
                "a.nome AS \"Nome\", a.paciente AS \"Paciente\", a.saram AS \"SARAM\", " +
                "a.categoria AS \"Categoria\", a.situacao AS \"Situação\", a.tipo AS \"Tipo\", " +
                "a.classificacao AS \"Classificação\", a.status AS \"Status\", u.nome AS \"Cadastrador\", " +
                "to_char(a.criado_em,'DD/MM/YYYY HH24:MI') AS \"Data\" " +
                "FROM atendimentos a JOIN usuarios u ON u.id=a.criado_por " +
                "WHERE a.criado_em >= ? AND a.criado_em < ? ORDER BY a.criado_em, a.id",
                mes.atDay(1).atStartOfDay(), mes.plusMonths(1).atDay(1).atStartOfDay());
        XlsxExport.write(response, "atendimentos-" + mes + ".xlsx", "Atendimentos", registros,
                List.of("ID Cadastro","Protocolo","Nome","Paciente","SARAM","Categoria","Situação","Tipo",
                        "Classificação","Status","Cadastrador","Data"));
    }

    @GetMapping("/relatorios/evolucoes-por-mes.xlsx") void evolucoesPorMes(
            @RequestParam @org.springframework.format.annotation.DateTimeFormat(pattern="yyyy-MM") YearMonth mes,
            HttpServletResponse response) throws IOException {
        var registros = rows("SELECT e.id AS \"ID Evolução\", a.id AS \"ID Cadastro\", " +
                "a.protocolo AS \"Protocolo\", a.nome AS \"Nome\", a.paciente AS \"Paciente\", " +
                "a.saram AS \"SARAM\", e.texto AS \"Evolução\", e.status_apos AS \"Status após evolução\", " +
                "u.nome AS \"Autor\", to_char(e.criado_em,'DD/MM/YYYY HH24:MI') AS \"Data\" " +
                "FROM evolucoes e JOIN atendimentos a ON a.id=e.atendimento_id " +
                "JOIN usuarios u ON u.id=e.autor_id WHERE e.criado_em >= ? AND e.criado_em < ? " +
                "ORDER BY e.criado_em, e.id",
                mes.atDay(1).atStartOfDay(), mes.plusMonths(1).atDay(1).atStartOfDay());
        XlsxExport.write(response, "evolucoes-" + mes + ".xlsx", "Evoluções", registros,
                List.of("ID Evolução","ID Cadastro","Protocolo","Nome","Paciente","SARAM","Evolução",
                        "Status após evolução","Autor","Data"));
    }

    @GetMapping("/usuarios") String usuarios(Model m, Principal p) {
        common(m,p);m.addAttribute("usuarioId",userId(p));m.addAttribute("registros",rows("SELECT id,nome,email,perfil,ativo,criado_em FROM usuarios ORDER BY id"));return "usuarios";
    }
    @PostMapping("/usuarios") String criarUsuario(@RequestParam String nome,@RequestParam String email,
            @RequestParam String senha,@RequestParam String perfil,RedirectAttributes flash) {
        if(nome.isBlank() || email.isBlank() || senha.length()<12 || !List.of("ADMIN","PROFISSIONAL").contains(perfil)) {
            flash.addFlashAttribute("erro","Informe nome, email, perfil válido e senha de pelo menos 12 caracteres.");return "redirect:/usuarios";
        }
        try { db.update("INSERT INTO usuarios(nome,email,senha_hash,perfil) VALUES (?,?,?,?)",nome.trim(),email.trim().toLowerCase(),encoder.encode(senha),perfil); flash.addFlashAttribute("mensagem","Usuário criado."); }
        catch(org.springframework.dao.DuplicateKeyException ex) { flash.addFlashAttribute("erro","Esse email já está cadastrado."); }
        return "redirect:/usuarios";
    }

    @PostMapping("/usuarios/{id}/senha") String redefinirSenha(@PathVariable long id,
            @RequestParam String novaSenha, RedirectAttributes flash) {
        if (novaSenha.length() < 12) {
            flash.addFlashAttribute("erro", "A nova senha precisa ter pelo menos 12 caracteres.");
            return "redirect:/usuarios";
        }
        int alterados = db.update("UPDATE usuarios SET senha_hash=? WHERE id=? AND ativo=true",
                encoder.encode(novaSenha), id);
        flash.addFlashAttribute(alterados == 1 ? "mensagem" : "erro",
                alterados == 1 ? "Senha redefinida. O usuário já pode entrar com a nova senha." : "Usuário ativo não encontrado.");
        return "redirect:/usuarios";
    }

    @PostMapping("/usuarios/{id}/excluir") String excluirUsuario(@PathVariable long id,
            Principal p, RedirectAttributes flash) {
        int alterados = db.update("UPDATE usuarios SET ativo=false WHERE id=? AND id<>? AND ativo=true",
                id, userId(p));
        flash.addFlashAttribute(alterados == 1 ? "mensagem" : "erro",
                alterados == 1 ? "Usuário excluído. Os registros anteriores foram preservados." : "Não foi possível excluir: usuário inexistente, já excluído ou sua própria conta.");
        return "redirect:/usuarios";
    }

    public static class AtendimentoForm {
        @NotBlank @Size(max=180) public String nome;
        @NotBlank @Size(max=180) public String paciente;
        @NotBlank @Size(max=30) public String saram;
        public String idade,bairro,residencia,celular,vinculo,meioContato,categoria,situacao,tipo,classificacao,estado,encaminhamento;
        @NotBlank @Size(max=1500) public String descricao;
        @Size(max=1500)
        public String getEncaminhamento(){return encaminhamento;}
        public String getNome(){return nome;}
        public void setNome(String value){this.nome=value;}
        public String getPaciente(){return paciente;}
        public void setPaciente(String value){this.paciente=value;}
        public String getSaram(){return saram;}
        public void setSaram(String value){this.saram=value;}
        public String getIdade(){return idade;}
        public void setIdade(String value){this.idade=value;}
        public String getBairro(){return bairro;}
        public void setBairro(String value){this.bairro=value;}
        public String getResidencia(){return residencia;}
        public void setResidencia(String value){this.residencia=value;}
        public String getCelular(){return celular;}
        public void setCelular(String value){this.celular=value;}
        public String getVinculo(){return vinculo;}
        public void setVinculo(String value){this.vinculo=value;}
        public String getMeioContato(){return meioContato;}
        public void setMeioContato(String value){this.meioContato=value;}
        public String getCategoria(){return categoria;}
        public void setCategoria(String value){this.categoria=value;}
        public String getSituacao(){return situacao;}
        public void setSituacao(String value){this.situacao=value;}
        public String getTipo(){return tipo;}
        public void setTipo(String value){this.tipo=value;}
        public String getClassificacao(){return classificacao;}
        public void setClassificacao(String value){this.classificacao=value;}
        public String getEstado(){return estado;}
        public void setEstado(String value){this.estado=value;}
        public String getDescricao(){return descricao;}
        public void setDescricao(String value){this.descricao=value;}
        public void setEncaminhamento(String value){this.encaminhamento=value;}
        public String getStatus(){return status;}
        public void setStatus(String value){this.status=value;}
        @jakarta.validation.constraints.Pattern(regexp="Pendente|Resolvido") public String status="Pendente";
    }
    public static class EvolucaoForm {
        @NotBlank @Size(max=1500) public String texto;
        @jakarta.validation.constraints.Pattern(regexp="Pendente|Resolvido") public String status;
        public int versao;
        public String getTexto(){return texto;}
        public void setTexto(String value){this.texto=value;}
        public String getStatus(){return status;}
        public void setStatus(String value){this.status=value;}
        public int getVersao(){return versao;}
        public void setVersao(int value){this.versao=value;}
    }
}
