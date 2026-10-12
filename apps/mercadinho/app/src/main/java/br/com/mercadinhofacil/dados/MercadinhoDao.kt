package br.com.mercadinhofacil.dados

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface MercadinhoDao {

    // ---------------------------------------------------------------- produtos

    @Query("SELECT * FROM produtos WHERE ativo = 1 ORDER BY nome COLLATE NOCASE")
    fun produtos(): Flow<List<Produto>>

    @Query("SELECT * FROM produtos WHERE ativo = 1 ORDER BY nome COLLATE NOCASE")
    suspend fun listaProdutos(): List<Produto>

    @Query("SELECT * FROM produtos WHERE id = :id")
    suspend fun produto(id: Long): Produto?

    @Query("SELECT * FROM produtos WHERE id = :id")
    fun observarProduto(id: Long): Flow<Produto?>

    @Query("SELECT * FROM produtos WHERE codigoBarras = :codigo AND ativo = 1 LIMIT 1")
    suspend fun produtoPorCodigo(codigo: String): Produto?

    @Insert
    suspend fun inserir(p: Produto): Long

    @Update
    suspend fun atualizar(p: Produto)

    @Query("UPDATE produtos SET estoque = estoque + :delta WHERE id = :id")
    suspend fun somarEstoque(id: Long, delta: Double)

    @Query("SELECT DISTINCT categoria FROM produtos WHERE ativo = 1 AND categoria != '' ORDER BY categoria")
    fun categorias(): Flow<List<String>>

    @Query(
        """SELECT i.produtoId AS produtoId, SUM(i.quantidade) AS quantidade, MAX(v.data) AS ultimaVenda
           FROM itens_venda i JOIN vendas v ON v.id = i.vendaId
           WHERE v.status != 'CANCELADA' AND v.data >= :desde AND i.produtoId IS NOT NULL
           GROUP BY i.produtoId"""
    )
    suspend fun giro(desde: Long): List<GiroProduto>

    @Insert
    suspend fun inserir(m: MovimentoEstoque): Long

    @Query("SELECT * FROM movimentos_estoque WHERE produtoId = :produtoId ORDER BY data DESC LIMIT 100")
    fun movimentosEstoque(produtoId: Long): Flow<List<MovimentoEstoque>>

    // ---------------------------------------------------------------- clientes

    @Query(
        """SELECT c.*,
             (SELECT COALESCE(SUM(v.total), 0) FROM vendas v WHERE v.clienteId = c.id AND v.status = 'FIADO')
             - (SELECT COALESCE(SUM(m.valor), 0) FROM movimentos m WHERE m.clienteId = c.id AND m.tipo = 'RECEBIMENTO_FIADO' AND m.cancelado = 0)
             AS divida,
             (SELECT MIN(v.data) FROM vendas v WHERE v.clienteId = c.id AND v.status = 'FIADO') AS fiadoMaisAntigo
           FROM clientes c WHERE c.ativo = 1 ORDER BY c.nome COLLATE NOCASE"""
    )
    fun clientes(): Flow<List<ClienteComDivida>>

    @Query(
        """SELECT c.*,
             (SELECT COALESCE(SUM(v.total), 0) FROM vendas v WHERE v.clienteId = c.id AND v.status = 'FIADO')
             - (SELECT COALESCE(SUM(m.valor), 0) FROM movimentos m WHERE m.clienteId = c.id AND m.tipo = 'RECEBIMENTO_FIADO' AND m.cancelado = 0)
             AS divida,
             (SELECT MIN(v.data) FROM vendas v WHERE v.clienteId = c.id AND v.status = 'FIADO') AS fiadoMaisAntigo
           FROM clientes c WHERE c.ativo = 1 ORDER BY c.nome COLLATE NOCASE"""
    )
    suspend fun listaClientes(): List<ClienteComDivida>

    @Query("SELECT * FROM clientes WHERE id = :id")
    suspend fun cliente(id: Long): Cliente?

    @Query("SELECT * FROM clientes WHERE id = :id")
    fun observarCliente(id: Long): Flow<Cliente?>

    @Insert
    suspend fun inserir(c: Cliente): Long

    @Update
    suspend fun atualizar(c: Cliente)

    @Query("SELECT * FROM vendas WHERE clienteId = :clienteId ORDER BY data DESC")
    fun vendasDoCliente(clienteId: Long): Flow<List<Venda>>

    @Query("SELECT * FROM vendas WHERE clienteId = :clienteId AND status = 'FIADO' ORDER BY data")
    suspend fun fiadosDoCliente(clienteId: Long): List<Venda>

    @Query("SELECT * FROM movimentos WHERE clienteId = :clienteId AND tipo = 'RECEBIMENTO_FIADO' ORDER BY data DESC")
    fun recebimentosDoCliente(clienteId: Long): Flow<List<Movimento>>

    @Query("SELECT COALESCE(SUM(valor), 0) FROM movimentos WHERE clienteId = :clienteId AND tipo = 'RECEBIMENTO_FIADO' AND cancelado = 0")
    suspend fun totalRecebidoDoCliente(clienteId: Long): Long

    // ---------------------------------------------------------------- contas

    @Query(
        """SELECT c.*, COALESCE((SELECT SUM(CASE WHEN m.entrada = 1 THEN m.valor ELSE -m.valor END)
             FROM movimentos m WHERE m.contaId = c.id AND m.cancelado = 0), 0) AS saldo
           FROM contas c WHERE c.ativa = 1 ORDER BY c.ordem, c.id"""
    )
    fun contas(): Flow<List<ContaComSaldo>>

    @Query(
        """SELECT c.*, COALESCE((SELECT SUM(CASE WHEN m.entrada = 1 THEN m.valor ELSE -m.valor END)
             FROM movimentos m WHERE m.contaId = c.id AND m.cancelado = 0), 0) AS saldo
           FROM contas c WHERE c.ativa = 1 ORDER BY c.ordem, c.id"""
    )
    suspend fun listaContas(): List<ContaComSaldo>

    @Query("SELECT * FROM contas WHERE id = :id")
    suspend fun conta(id: Long): Conta?

    @Query("SELECT * FROM contas WHERE tipo = :tipo AND ativa = 1 ORDER BY ordem, id LIMIT 1")
    suspend fun contaPorTipo(tipo: String): Conta?

    @Query("SELECT COUNT(*) FROM contas")
    suspend fun quantidadeContas(): Int

    @Insert
    suspend fun inserir(c: Conta): Long

    @Update
    suspend fun atualizar(c: Conta)

    // ---------------------------------------------------------------- vendas

    @Insert
    suspend fun inserir(v: Venda): Long

    @Update
    suspend fun atualizar(v: Venda)

    @Insert
    suspend fun inserirItens(itens: List<ItemVenda>)

    @Transaction
    @Query("SELECT * FROM vendas WHERE id = :id")
    suspend fun vendaCompleta(id: Long): VendaComItens?

    @Transaction
    @Query("SELECT * FROM vendas WHERE id = :id")
    fun observarVenda(id: Long): Flow<VendaComItens?>

    @Transaction
    @Query("SELECT * FROM vendas WHERE data >= :inicio AND data < :fim AND status != 'CANCELADA' ORDER BY data")
    suspend fun vendasNoPeriodo(inicio: Long, fim: Long): List<VendaComItens>

    @Transaction
    @Query("SELECT * FROM vendas ORDER BY data DESC LIMIT :limite")
    fun ultimasVendas(limite: Int = 200): Flow<List<VendaComItens>>

    @Query("SELECT * FROM vendas WHERE status != 'CANCELADA' ORDER BY data DESC LIMIT 1")
    suspend fun ultimaVenda(): Venda?

    @Query("SELECT * FROM vendas WHERE status = 'PENDENTE' ORDER BY data DESC")
    fun pixPendentes(): Flow<List<Venda>>

    @Query("SELECT * FROM vendas WHERE status = 'PENDENTE' ORDER BY data DESC")
    suspend fun listaPixPendentes(): List<Venda>

    @Query("SELECT COALESCE(SUM(total), 0) FROM vendas WHERE data >= :inicio AND status != 'CANCELADA'")
    fun totalVendidoDesde(inicio: Long): Flow<Long>

    @Query("SELECT COUNT(*) FROM vendas WHERE data >= :inicio AND status != 'CANCELADA'")
    fun quantidadeVendasDesde(inicio: Long): Flow<Int>

    // ---------------------------------------------------------------- movimentos

    @Insert
    suspend fun inserir(m: Movimento): Long

    @Query("SELECT * FROM movimentos WHERE id = :id")
    suspend fun movimento(id: Long): Movimento?

    @Query("UPDATE movimentos SET cancelado = 1 WHERE id = :id")
    suspend fun cancelarMovimento(id: Long)

    @Query("UPDATE movimentos SET cancelado = 1 WHERE grupo = :grupo")
    suspend fun cancelarGrupo(grupo: String)

    @Query("UPDATE movimentos SET cancelado = 1 WHERE vendaId = :vendaId")
    suspend fun cancelarMovimentosDaVenda(vendaId: Long)

    @Query("SELECT * FROM movimentos WHERE contaId = :contaId ORDER BY data DESC LIMIT 300")
    fun movimentosDaConta(contaId: Long): Flow<List<Movimento>>

    @Query("SELECT * FROM movimentos ORDER BY data DESC LIMIT 300")
    fun ultimosMovimentos(): Flow<List<Movimento>>

    @Query("SELECT * FROM movimentos WHERE data >= :inicio AND data < :fim AND cancelado = 0 ORDER BY data")
    suspend fun movimentosNoPeriodo(inicio: Long, fim: Long): List<Movimento>

    @Insert
    suspend fun inserir(f: Fechamento): Long

    @Query("SELECT * FROM fechamentos WHERE contaId = :contaId ORDER BY data DESC LIMIT 30")
    fun fechamentos(contaId: Long): Flow<List<Fechamento>>

    // ---------------------------------------------------------------- histórico

    @Insert
    suspend fun inserir(h: Historico): Long

    @Query("SELECT * FROM historico ORDER BY data DESC LIMIT 500")
    fun historico(): Flow<List<Historico>>
}
