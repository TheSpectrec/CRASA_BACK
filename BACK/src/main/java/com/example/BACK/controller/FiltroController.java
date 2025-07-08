package com.example.BACK.controller;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.BACK.model.Company;
import com.example.BACK.model.Customer;
import com.example.BACK.model.Family;
import com.example.BACK.model.Mark;
import com.example.BACK.model.Product;
import com.example.BACK.model.User;
import com.example.BACK.service.CompanyService;
import com.example.BACK.service.CustomerService;
import com.example.BACK.service.FamilyService;
import com.example.BACK.service.MarkService;
import com.example.BACK.service.ProductService;
import com.example.BACK.service.UserService;

@RestController
@RequestMapping("/api/filtros")
public class FiltroController {

    @Autowired
    private CustomerService customerService;

    @Autowired
    private ProductService productService;

    @Autowired
    private MarkService markService;

    @Autowired
    private CompanyService companyService;

    @Autowired
    private FamilyService familyService;

    @Autowired
    private UserService userService;

    // 🔄 Clientes por producto
    @GetMapping("/clientes/producto/{productId}")
    public List<Customer> getClientesPorProducto(@PathVariable String productId) {
        return customerService.findByProductId(productId);
    }

    // 🔄 Productos por cliente
    @GetMapping("/productos/cliente/{customerId}")
    public List<Product> getProductosPorCliente(@PathVariable String customerId) {
        return productService.findByCustomerId(customerId);
    }

    // 🔄 Marcas por empresa
    @GetMapping("/marcas/empresa/{companyId}")
    public List<Mark> getMarcasPorEmpresa(@PathVariable String companyId) {
        return markService.findAll().stream()
                .filter(m -> m.getCompany() != null && m.getCompany().getId().equals(companyId))
                .collect(Collectors.toList());
    }

    // 🔄 Familias por marca
    @GetMapping("/familias/marca/{markId}")
    public List<Family> getFamiliasPorMarca(@PathVariable String markId) {
        return familyService.findAll().stream()
                .filter(f -> f.getMark() != null && f.getMark().getId().equals(markId))
                .collect(Collectors.toList());
    }

    // 🔄 Productos por familia
    @GetMapping("/productos/familia/{familyId}")
    public List<Product> getProductosPorFamilia(@PathVariable String familyId) {
        return productService.findAll().stream()
                .filter(p -> p.getFamily() != null && p.getFamily().getId().equals(familyId))
                .collect(Collectors.toList());
    }

    // 🔄 Empresa por producto
    @GetMapping("/empresa/producto/{productId}")
    public Company getEmpresaPorProducto(@PathVariable String productId) {
        return productService.findById(productId)
                .map(p -> p.getFamily() != null && p.getFamily().getMark() != null
                        ? p.getFamily().getMark().getCompany()
                        : null)
                .orElse(null);
    }

    // 🔄 Vendedor por cliente
    @GetMapping("/vendedor/cliente/{customerId}")
    public User getVendedorPorCliente(@PathVariable String customerId) {
        return customerService.findById(customerId)
                .map(Customer::getVendedor)
                .orElse(null);
    }

    // 🔄 Clientes por vendedor
    @GetMapping("/clientes/vendedor/{vendedorId}")
    public List<Customer> getClientesPorVendedor(@PathVariable String vendedorId) {
        return customerService.findByVendedorId(vendedorId);
    }
}
