package com.easy.query.plugin.action.navgen;

import javax.swing.*;
import javax.swing.plaf.basic.BasicComboBoxUI;
import java.awt.*;
import java.awt.geom.Line2D;
import java.util.ArrayList;
import java.util.List;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;

public class NavMappingPanel extends JPanel {
    private JComboBox<String> mappingTypeCombo;
    private List<AttributeGroup> attributeGroups;
    private JButton addGroupButton;
    private JButton confirmButton;
    private JPanel attributesPanel;
    private static final int INITIAL_GROUP_Y = 150;
    private static final int GROUP_VERTICAL_GAP = 70;
    private static final int MANY_TO_MANY_EXTRA_GAP = 30;
    private JComboBox<String> entitySelector;
    private JComboBox<String> middleEntitySelector;

    public static class MappingData {
        private String mappingType;
        private List<AttributeMapping> attributeMappings;

        public MappingData(String mappingType, List<AttributeMapping> attributeMappings) {
            this.mappingType = mappingType;
            this.attributeMappings = attributeMappings;
        }

        public String getMappingType() {
            return mappingType;
        }

        public List<AttributeMapping> getAttributeMappings() {
            return attributeMappings;
        }
    }

    public static class AttributeMapping {
        private String sourceAttribute;
        private String middleSourceAttribute;
        private String middleTargetAttribute;
        private String targetAttribute;

        public AttributeMapping(String sourceAttribute, String middleSourceAttribute,
                String middleTargetAttribute, String targetAttribute) {
            this.sourceAttribute = sourceAttribute;
            this.middleSourceAttribute = middleSourceAttribute;
            this.middleTargetAttribute = middleTargetAttribute;
            this.targetAttribute = targetAttribute;
        }

        public String getSourceAttribute() {
            return sourceAttribute;
        }

        public String getMiddleSourceAttribute() {
            return middleSourceAttribute;
        }

        public String getMiddleTargetAttribute() {
            return middleTargetAttribute;
        }

        public String getTargetAttribute() {
            return targetAttribute;
        }
    }

    private class AttributeGroup {
        JComboBox<String> sourceAttr;
        JComboBox<String> middleAttr;
        JComboBox<String> middleTargetAttr;
        JComboBox<String> targetAttr;
        JButton deleteButton;
        int yPosition;

        public AttributeGroup(int yPos) {
            this.yPosition = yPos;
            initializeComponents();
        }

        private void initializeComponents() {
            String[] sourceAttributes = new String[] {
                    "当前实体属性",
                    "id",
                    "name",
                    "code",
                    "description"
            };
            String[] mappingAttributes = new String[] {
                    "映射实体属性",
                    "source_id",
                    "source_code",
                    "target_id",
                    "target_code"
            };
            String[] targetAttributes = new String[] {
                    "目标实体属性",
                    "id",
                    "name",
                    "code",
                    "status"
            };

            sourceAttr = new JComboBox<>(sourceAttributes);
            middleAttr = new JComboBox<>(mappingAttributes);
            middleTargetAttr = new JComboBox<>(mappingAttributes);
            targetAttr = new JComboBox<>(targetAttributes);
            deleteButton = new JButton("删除");

            // 设置组件位置 - 调整起始位置以适应新布局
            sourceAttr.setBounds(50, yPosition, 150, 30);
            middleAttr.setBounds(350, yPosition, 150, 30);
            middleTargetAttr.setBounds(350, yPosition + 50, 150, 30);
            targetAttr.setBounds(650, yPosition, 150, 30);
            deleteButton.setBounds(820, yPosition, 60, 30);

            // 配置样式
            configureComboBoxStyle(sourceAttr, middleAttr, middleTargetAttr, targetAttr);
            deleteButton.setVisible(false); // 第一组默认不显示删除按钮
        }
    }

    public NavMappingPanel() {
        setLayout(null);
        setPreferredSize(new Dimension(900, 600));

        // 映射类型标签
        JLabel mappingTypeLabel = new JLabel("映射类型:");
        mappingTypeLabel.setBounds(50, 20, 70, 30);
        add(mappingTypeLabel);

        // 映射类型下拉框
        mappingTypeCombo = new JComboBox<>(new String[] { "OneToOne", "OneToMany", "ManyToOne", "ManyToMany" });
        mappingTypeCombo.setSelectedItem("OneToOne");
        mappingTypeCombo.setBounds(120, 20, 150, 30);
        mappingTypeCombo.addActionListener(e -> updateMappingDisplay());
        add(mappingTypeCombo);

        // 中间实体标签和下拉框
        JLabel middleLabel = new JLabel("中间实体(可选):");
        middleLabel.setBounds(50, 55, 70, 30);
        add(middleLabel);

        middleEntitySelector = new JComboBox<>();
        middleEntitySelector.addItem("");
        middleEntitySelector.addItem("中间实体1");
        middleEntitySelector.addItem("中间实体2");
        middleEntitySelector.setBounds(120, 55, 150, 30);
        middleEntitySelector.addActionListener(e -> updateMappingDisplay());
        add(middleEntitySelector);

        // 目标实体标签和下拉框
        JLabel targetLabel = new JLabel("目标实体(必须):");
        targetLabel.setBounds(50, 90, 70, 30);
        add(targetLabel);

        entitySelector = new JComboBox<>();
        entitySelector.addItem("实体1");
        entitySelector.addItem("实体2");
        entitySelector.setBounds(120, 90, 150, 30);
        add(entitySelector);

        // 根据映射类型控制中间实体选择器的可见性
        middleLabel.setVisible(false);
        middleEntitySelector.setVisible(false);

        // 添加映射组按钮
        addGroupButton = new JButton("添加映射组");
        addGroupButton.setBounds(750, 20, 100, 30);
        add(addGroupButton);
        addGroupButton.addActionListener(e -> addAttributeGroup());

        // 确认按钮
        confirmButton = new JButton("确认");
        confirmButton.setBounds(750, 500, 80, 30);
        add(confirmButton);
        confirmButton.addActionListener(e -> handleConfirm());

        attributeGroups = new ArrayList<>();
        addAttributeGroup();
        updateMappingDisplay();
    }

    private void initComponents() {
        // 映射类型标签和下拉框放在同一行
        JLabel mappingTypeLabel = new JLabel("映射类型:");
        mappingTypeLabel.setBounds(50, 20, 70, 30);
        add(mappingTypeLabel);

        mappingTypeCombo = new JComboBox<>(new String[] { "OneToOne", "OneToMany", "ManyToOne", "ManyToMany" });
        mappingTypeCombo.setSelectedItem("OneToOne");
        mappingTypeCombo.setBounds(120, 20, 150, 30);
        mappingTypeCombo.addActionListener(e -> updateMappingDisplay());

        // 添加映射组按钮使用固定位置
        addGroupButton = new JButton("添加映射组");
        addGroupButton.setBounds(750, 20, 100, 30);
        addGroupButton.addActionListener(e -> addAttributeGroup());

        // 确认按钮使用固定位置
        confirmButton = new JButton("确认");
        confirmButton.addActionListener(e -> handleConfirm());
        confirmButton.setBounds(750, 500, 80, 30);

        add(mappingTypeLabel);
        add(mappingTypeCombo);
        add(addGroupButton);
        add(confirmButton);
    }

    private void addAttributeGroup() {
        String selectedType = (String) mappingTypeCombo.getSelectedItem();
        boolean isManyToMany = "ManyToMany".equals(selectedType);

        // 计算新组的Y位置，考虑ManyToMany模式
        int yPos = INITIAL_GROUP_Y;
        if (attributeGroups.size() > 0) {
            AttributeGroup lastGroup = attributeGroups.get(attributeGroups.size() - 1);
            yPos = lastGroup.yPosition + GROUP_VERTICAL_GAP;
            if (isManyToMany) {
                yPos += MANY_TO_MANY_EXTRA_GAP; // ManyToMany模式增加额外间距
            }
        }

        AttributeGroup group = new AttributeGroup(yPos);
        attributeGroups.add(group);

        // 添加组件到面板
        add(group.sourceAttr);
        add(group.middleAttr);
        add(group.middleTargetAttr);
        add(group.targetAttr);
        add(group.deleteButton);

        // 如果不是第一组，显示删除按钮
        if (attributeGroups.size() > 1) {
            group.deleteButton.setVisible(true);
            group.deleteButton.addActionListener(e -> removeAttributeGroup(group));
        }

        updateMappingDisplay();
        revalidate();
        repaint();
    }

    private void removeAttributeGroup(AttributeGroup group) {
        remove(group.sourceAttr);
        remove(group.middleAttr);
        remove(group.middleTargetAttr);
        remove(group.targetAttr);
        remove(group.deleteButton);

        attributeGroups.remove(group);

        // 重新调整剩余组的位置
        String selectedType = (String) mappingTypeCombo.getSelectedItem();
        boolean isManyToMany = "ManyToMany".equals(selectedType);

        int newY = INITIAL_GROUP_Y;
        for (AttributeGroup currentGroup : attributeGroups) {
            updateGroupPosition(currentGroup, newY);
            newY += GROUP_VERTICAL_GAP;
            if (isManyToMany) {
                newY += MANY_TO_MANY_EXTRA_GAP;
            }
        }

        revalidate();
        repaint();
    }

    private void updateGroupPosition(AttributeGroup group, int newY) {
        group.yPosition = newY;
        group.sourceAttr.setBounds(50, newY, 150, 30);

        String selectedType = (String) mappingTypeCombo.getSelectedItem();
        boolean isManyToMany = "ManyToMany".equals(selectedType);
        boolean hasMiddleEntity = isManyToMany &&
                middleEntitySelector.getSelectedItem() != null &&
                !middleEntitySelector.getSelectedItem().toString().isEmpty();

        if (isManyToMany && hasMiddleEntity) {
            // 有中间实体时的布局
            group.middleAttr.setBounds(350, newY, 150, 30);
            group.middleTargetAttr.setBounds(350, newY + 35, 150, 30);
            group.targetAttr.setBounds(650, newY + 35, 150, 30);
            group.deleteButton.setBounds(820, newY + 15, 60, 30);
        } else {
            // 无中间实体时的布局
            group.targetAttr.setBounds(650, newY, 150, 30);
            group.deleteButton.setBounds(820, newY, 60, 30);
        }
    }

    private void updateMappingDisplay() {
        String selectedType = (String) mappingTypeCombo.getSelectedItem();
        boolean isManyToMany = "ManyToMany".equals(selectedType);
        boolean hasMiddleEntity = isManyToMany &&
                middleEntitySelector.getSelectedItem() != null &&
                !middleEntitySelector.getSelectedItem().toString().isEmpty();

        // 控制中间实体选择器的可见性
        Component[] components = getComponents();
        for (Component component : components) {
            if (component instanceof JLabel) {
                JLabel label = (JLabel) component;
                if (label.getText().equals("中间实体(可选):")) {
                    label.setVisible(isManyToMany);
                }
            } else if (component == middleEntitySelector) {
                component.setVisible(isManyToMany);
            }
        }

        // 重新调整所有组的位置
        int newY = INITIAL_GROUP_Y;
        for (AttributeGroup group : attributeGroups) {
            // 只在选择了中间实体时显示中间实体属性
            group.middleAttr.setVisible(hasMiddleEntity);
            group.middleTargetAttr.setVisible(hasMiddleEntity);

            updateGroupPosition(group, newY);
            newY += GROUP_VERTICAL_GAP;
            if (hasMiddleEntity) {
                newY += MANY_TO_MANY_EXTRA_GAP;
            }
        }

        repaint();
    }

    private void configureComboBoxStyle(JComboBox<?>... comboBoxes) {
        for (JComboBox<?> box : comboBoxes) {
            box.setBackground(Color.WHITE);
            box.setFont(new Font("宋体", Font.PLAIN, 12));
            // 设置下拉框边框
            box.setBorder(BorderFactory.createLineBorder(Color.GRAY));
            // 设置下拉箭头的颜色和大小
            box.setUI(new BasicComboBoxUI() {
                @Override
                protected JButton createArrowButton() {
                    JButton button = super.createArrowButton();
                    button.setBackground(Color.WHITE);
                    return button;
                }
            });
            box.setEditable(true);
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g;
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        String selectedType = (String) mappingTypeCombo.getSelectedItem();
        boolean isManyToMany = "ManyToMany".equals(selectedType);
        boolean hasMiddleEntity = isManyToMany &&
                middleEntitySelector.getSelectedItem() != null &&
                !middleEntitySelector.getSelectedItem().toString().isEmpty();

        // 绘制映射组之间的分隔线
        g2d.setColor(new Color(200, 200, 200));
        g2d.setStroke(new BasicStroke(1, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL,
                0, new float[] { 5 }, 0));

        // 为每个映射组绘制底部分隔线
        for (int i = 0; i < attributeGroups.size() - 1; i++) {
            AttributeGroup group = attributeGroups.get(i);
            int lineY = group.yPosition + (hasMiddleEntity ? 80 : 40);
            g2d.drawLine(30, lineY, getWidth() - 30, lineY);
        }

        // 恢复实线样式和颜色用于绘制连接线
        g2d.setStroke(new BasicStroke(2));
        g2d.setColor(Color.BLACK);

        if (isManyToMany && hasMiddleEntity) {
            // 有中间实体时的连线
            for (AttributeGroup group : attributeGroups) {
                int sourceY = group.yPosition + 15;
                int middleY1 = group.yPosition + 15;
                g2d.draw(new Line2D.Double(200, sourceY, 350, middleY1));
                drawArrow(g2d, 340, middleY1);

                int middleY2 = group.yPosition + 55;
                int targetY = group.yPosition + 55;
                g2d.draw(new Line2D.Double(500, middleY2, 650, targetY));
                drawArrow(g2d, 640, targetY);
            }
        } else {
            // 无中间实体时的直接连线
            for (AttributeGroup group : attributeGroups) {
                int y = group.yPosition + 15;
                g2d.draw(new Line2D.Double(200, y, 650, y));
                drawArrow(g2d, 640, y);
            }
        }
    }

    private void drawArrow(Graphics2D g2d, int x, int y) {
        int[] xPoints = { x, x - 10, x - 10 };
        int[] yPoints = { y, y - 5, y + 5 };
        g2d.fillPolygon(xPoints, yPoints, 3);
    }

    private void handleConfirm() {
        MappingData mappingData = collectFormData();
        // 这里可以添加处理收集到的数据的逻辑
        System.out.println("Mapping Type: " + mappingData.getMappingType());
        for (AttributeMapping mapping : mappingData.getAttributeMappings()) {
            System.out.println("Source: " + mapping.getSourceAttribute());
            if ("ManyToMany".equals(mappingData.getMappingType())) {
                System.out.println("Middle Source: " + mapping.getMiddleSourceAttribute());
                System.out.println("Middle Target: " + mapping.getMiddleTargetAttribute());
            }
            System.out.println("Target: " + mapping.getTargetAttribute());
        }
    }

    public MappingData collectFormData() {
        String mappingType = (String) mappingTypeCombo.getSelectedItem();
        List<AttributeMapping> attributeMappings = new ArrayList<>();

        for (AttributeGroup group : attributeGroups) {
            String sourceAttr = (String) group.sourceAttr.getSelectedItem();
            String middleAttr = (String) group.middleAttr.getSelectedItem();
            String middleTargetAttr = (String) group.middleTargetAttr.getSelectedItem();
            String targetAttr = (String) group.targetAttr.getSelectedItem();

            attributeMappings.add(new AttributeMapping(
                    sourceAttr,
                    middleAttr,
                    middleTargetAttr,
                    targetAttr));
        }

        return new MappingData(mappingType, attributeMappings);
    }

    // 获取选中的实体
    public String getSelectedEntity() {
        return (String) entitySelector.getSelectedItem();
    }

    // 新增获取选中的中间实体的方法
    public String getSelectedMiddleEntity() {
        return (String) middleEntitySelector.getSelectedItem();
    }
}